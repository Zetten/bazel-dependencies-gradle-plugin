package com.github.zetten.bazeldeps

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.hash.Hashing
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@CacheableTask
open class GenerateJvmMavenImportExternal @Inject constructor(private val workerExecutor: WorkerExecutor) :
    DefaultTask() {

    @Input
    val dependencies: SetProperty<ProjectDependency> = project.objects.setProperty()

    @Input
    val repositories: ListProperty<String> = project.objects.listProperty()

    @Input
    val licenseData: MapProperty<ProjectDependency, List<LicenseData>> = project.objects.mapProperty()

    @Input
    val strictLicenses: Property<Boolean> = project.objects.property<Boolean>().convention(true)

    @Input
    val dependenciesAttr: Property<String> = project.objects.property()

    @Input
    val safeSources: Property<Boolean> = project.objects.property<Boolean>().convention(false)

    @OutputFile
    val outputFile: Property<File> = project.objects.property()

    @TaskAction
    fun generateWorkspace() {
        logger.warn("Generating Bazel repository rules for {} dependencies", dependencies.get().size)

        val output = outputFile.get()
        output.parentFile.mkdirs()

        val sortedDependencies = dependencies.get().sorted()

        val snippetFiles = sortedDependencies.map { dependency ->
            val snippetFile = temporaryDir.resolve("${dependency.getBazelIdentifier()}.snippet")
            workerExecutor.noIsolation().submit(GenerateDependencySnippet::class.java) {
                getOutputFile().set(snippetFile)
                getDependency().set(dependency)
                getRepositories().set(repositories)
                getLicenseData().set(licenseData.map { it[dependency] ?: emptyList() })
                getStrictLicenses().set(strictLicenses)
                getDependenciesAttr().set(dependenciesAttr)
                getSafeSources().set(safeSources)
            }
            snippetFile
        }

        output.writeText(
            """
            |load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")
            |
            |def _replace_dependencies(dependencies, replacements):
            |    new_dependencies = depset()
            |    for dep in dependencies:
            |        if dep in replacements.keys():
            |            new_dependencies = depset(transitive = [new_dependencies, depset(direct = replacements.get(dep))])
            |        else:
            |            new_dependencies = depset(transitive = [new_dependencies, depset(direct = [dep])])
            |    return new_dependencies.to_list()

            |def java_repositories(
            |""".trimMargin()
        )
        sortedDependencies.forEach {
            output.appendText("        omit_${it.getBazelIdentifier()} = False,\n")
        }
        output.appendText(
            """
            |        fetch_sources = False,
            |        replacements = {}):
            |""".trimMargin()
        )
        sortedDependencies.forEach {
            output.appendText("    if not omit_${it.getBazelIdentifier()}:\n        ${it.getBazelIdentifier()}(fetch_sources, replacements)\n")
        }
        output.appendText("\n")

        workerExecutor.await()

        if (snippetFiles.isEmpty()) {
            output.appendText("    pass\n")
        } else {
            snippetFiles.sorted().forEachIndexed { idx, it ->
                output.appendBytes(it.readBytes())
                if (idx != snippetFiles.lastIndex) output.appendText("\n")
            }
        }
    }
}

interface GenerateDependencySnippetParams : WorkParameters {
    fun getOutputFile(): RegularFileProperty
    fun getDependency(): Property<ProjectDependency>
    fun getRepositories(): ListProperty<String>
    fun getLicenseData(): ListProperty<LicenseData>
    fun getStrictLicenses(): Property<Boolean>
    fun getDependenciesAttr(): Property<String>
    fun getSafeSources(): Property<Boolean>
}

abstract class GenerateDependencySnippet : WorkAction<GenerateDependencySnippetParams> {
    private val logger: Logger = LoggerFactory.getLogger(GenerateDependencySnippet::class.java)

    override fun execute() {
        val outputFile = parameters.getOutputFile().asFile.get()
        val dependency = parameters.getDependency().get()
        val repositories = parameters.getRepositories().get()
        val licenseData = parameters.getLicenseData().get()
        val strictLicenses = parameters.getStrictLicenses().get()
        val dependenciesAttr = parameters.getDependenciesAttr().get()
        val safeSources = parameters.getSafeSources().get()

        logger.info("Generating Bazel repository rule for ${dependency.id}")

        val mostRestrictiveLicense = try {
            Licenses.getMostRestrictiveLicense(licenseData)
        } catch (e: Exception) {
            if (strictLicenses) throw IllegalStateException(
                "Could not determine a license for ${dependency.getMavenIdentifier()}",
                e
            )
            "none"
        }

        val jarSha256 = Hashing.sha256().hashFile(dependency.jar!!).toZeroPaddedString(64)
        val serverUrls = repositories.filter { artifactExists(dependency, it) }
        val srcjarSha256 = if (dependency.srcJar != null) "\"${
            Hashing.sha256().hashFile(dependency.srcJar).toZeroPaddedString(64)
        }\"" else "None"

        outputFile.writeText("""
                |def ${dependency.getBazelIdentifier()}(fetch_sources, replacements):
                |    jvm_maven_import_external(
                |        name = "${dependency.getBazelIdentifier()}",
                |        artifact = "${dependency.getJvmMavenImportExternalCoordinates()}",
                |        ${
            serverUrls.sorted().joinToString(
                "\n",
                prefix = "server_urls = [\n",
                postfix = "\n        "
            ) { "            \"${it}\"," }
        }],
                |        artifact_sha256 = "$jarSha256",
                |        licenses = ["$mostRestrictiveLicense"],
                |        fetch_sources = ${getFetchSources(safeSources, dependency, repositories)},
                |        srcjar_sha256 = ${srcjarSha256},
                |        ${
            dependency.dependencies.map { it.getBazelIdentifier() }.joinToString(
                "",
                prefix = "$dependenciesAttr = _replace_dependencies([",
                postfix = "\n        "
            ) { "\n            \"@${it}\"," }
        }], replacements),
                |        tags = [
                |            "maven_coordinates=${dependency.getMavenCoordinatesTag()}",
                |        ],
                |    )
                |""".trimMargin()
        )
    }

    private fun artifactExists(dependency: ProjectDependency, repository: String): Boolean {
        val artifactUrl = "${dependency.getArtifactUrl(repository)}.jar"
        val code = with(URL(artifactUrl).openConnection() as HttpURLConnection) {
            requestMethod = "HEAD"
            connect()
            responseCode
        }
        logger.debug("Received $code for sources artifact at $artifactUrl")
        return (code in 100..399)
    }

    private fun getFetchSources(
        safeSources: Boolean,
        dependency: ProjectDependency,
        repositories: List<String>
    ) = if (safeSources && !sourcesExists(dependency, repositories)) "False" else "fetch_sources"

    private fun sourcesExists(dependency: ProjectDependency, repositories: List<String>): Boolean {
        for (it in repositories) {
            val artifactUrl = "${dependency.getArtifactUrl(it)}-sources.jar"
            val code = with(URL(artifactUrl).openConnection() as HttpURLConnection) {
                requestMethod = "HEAD"
                connect()
                responseCode
            }
            logger.debug("Received $code for sources artifact at $artifactUrl")
            if (code in 100..399) return true
        }
        return false
    }
}
