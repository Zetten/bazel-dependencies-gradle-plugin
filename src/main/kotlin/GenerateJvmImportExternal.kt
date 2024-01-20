package com.github.zetten.bazeldeps

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.hash.Hashing
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
open class GenerateJvmImportExternal @Inject constructor(private val workerExecutor: WorkerExecutor) :
    DefaultTask() {

    @Input
    val dependencies: SetProperty<ProjectDependency> = project.objects.setProperty()

    @Input
    val repositories: ListProperty<String> = project.objects.listProperty()

    @Input
    val createAggregatorRepo: Property<Boolean> = project.objects.property<Boolean>()

    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun generateBazelJavaRepositories() {
        val sortedDependencies = dependencies.get().toSortedSet()

        logger.info("Generating Bazel repository rules for {} dependencies", sortedDependencies.size)

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()

        val processedDeps = sortedDependencies.map { dependency ->
            val id = dependency.getBazelIdentifier()
            val snippetFile = temporaryDir.resolve("$id.snippet")
            workerExecutor.noIsolation().submit(GenerateDependencySnippet::class.java) {
                getOutputFile().set(snippetFile)
                getDependency().set(dependency)
                getRepositories().set(repositories)
            }
            ProcessedDep(
                snippetFile,
                "$id(replacements, fetch_sources)",
                listOf(
                    """
                    |alias(
                    |    name = "$id",
                    |    actual = "@$id",
                    |    visibility = ["//visibility:public"],
                    |)
                    """.trimMargin(),
                    """
                    |alias(
                    |    name = "${id}_neverlink",
                    |    actual = "@${id}_neverlink",
                    |    visibility = ["//visibility:public"],
                    |)
                    """.trimMargin()
                )
            )
        }

        val repoFunctionNames = processedDeps.map { it.repoFunctionCall }
        val aggregatorRepoAliases = processedDeps.flatMap { it.aggregatorRepoAliases }

        output.writeText(
            """
            |load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_import_external")
            |load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
            |
            |def _replace_dependencies(dependencies, replacements):
            |    new_dependencies = depset()
            |    for dep in dependencies:
            |        if dep in replacements.keys():
            |            new_dependencies = depset(transitive = [new_dependencies, depset(direct = replacements.get(dep))])
            |        else:
            |            new_dependencies = depset(transitive = [new_dependencies, depset(direct = [dep])])
            |    return new_dependencies.to_list()
            |
            |def java_repositories(replacements = {}, fetch_sources = True):
            |    # Load the default and neverlink versions of each dependency
            |    ${indentedStrings(repoFunctionNames, indent = 0, separator = "", prefix = "")}
            """.trimMargin()
        )

        workerExecutor.await()

        val snippetFiles = processedDeps.map { it.snippetFile }
        if (snippetFiles.isEmpty()) {
            output.appendText("    pass\n")
        } else {
            snippetFiles.sorted().forEach { snippet ->
                output.appendText("\n")
                output.appendBytes(snippet.readBytes())
                output.appendText("\n")
            }
        }

        if (createAggregatorRepo.get()) {
            output.appendText(
                """
                |
                |REPOSITORIES_AGGREGATOR_CONTENT = ${"\"\"\""}
                |${aggregatorRepoAliases.joinToString("\n\n")}
                |${"\"\"\""}
                |
                |def _java_repositories_aggregator(repository_ctx):
                |    repository_ctx.file(
                |        "BUILD.bazel",
                |        REPOSITORIES_AGGREGATOR_CONTENT,
                |        executable = False,
                |    )
                |
                |java_repositories_aggregator = repository_rule(
                |    attrs = {},
                |    implementation = _java_repositories_aggregator,
                |)
                """.trimMargin()
            )
            // make sure we're aligned with buildifier
            output.appendText("\n")
        }
    }
}

private data class ProcessedDep(
    val snippetFile: File,
    val repoFunctionCall: String,
    val aggregatorRepoAliases: List<String>,
)

private interface GenerateDependencySnippetParams : WorkParameters {
    fun getOutputFile(): RegularFileProperty
    fun getDependency(): Property<ProjectDependency>
    fun getRepositories(): ListProperty<String>
}

private abstract class GenerateDependencySnippet : WorkAction<GenerateDependencySnippetParams> {
    override fun execute() {
        val outputFile = parameters.getOutputFile().asFile.get()
        val dependency = parameters.getDependency().get()
        val repositories = parameters.getRepositories().get()

        // calculate artifact attributes
        val artifactUrls = dependency.findArtifactUrls(repositories).map { "\"$it\"" }
        val jarSha256 = Hashing.sha256().hashFile(dependency.jar!!).toZeroPaddedString(64)
        val srcjarUrls = dependency.findSrcjarUrls(repositories).map { "\"$it\"" }
        val srcjarSha256 = dependency.srcJar?.let { Hashing.sha256().hashFile(it).toZeroPaddedString(64) }

        val bazelIdentifier = dependency.getBazelIdentifier()

        val deps = (dependency.compileOnlyDependencies.map { d -> "\"@${d.getBazelIdentifier()}_neverlink\"" } +
                dependency.dependencies.map { d -> "\"@${d.getBazelIdentifier()}\"" })
        val runtimeDeps = dependency.runtimeOnlyDependencies.map { d -> "\"@${d.getBazelIdentifier()}\"" }

        val commonArgs = buildList {
            add("rule_name = \"java_import\"")
            add("artifact_urls = [${indentedStrings(artifactUrls)}]")
            add("artifact_sha256 = \"$jarSha256\"")
            if (dependency.srcJar != null) add("srcjar_urls = fetch_sources and [${indentedStrings(srcjarUrls)}]")
            if (dependency.srcJar != null) add("srcjar_sha256 = \"$srcjarSha256\"")
            add("deps = _replace_dependencies([${indentedStrings(deps)}], replacements)")
            add("runtime_deps = _replace_dependencies([${indentedStrings(runtimeDeps)}], replacements)")
            if (dependency.testonly) add("testonly = True")
        }
        val args = buildList {
            add("jvm_import_external")
            add("name = \"$bazelIdentifier\"")
            addAll(commonArgs)
            if (dependency.neverlink) add("neverlink = True")
        }
        val neverlinkArgs = buildList {
            add("jvm_import_external")
            add("name = \"${bazelIdentifier}_neverlink\"")
            addAll(commonArgs)
            add("neverlink = True")
        }

        outputFile.writeText(
            """
            |def $bazelIdentifier(replacements = [], fetch_sources = True):
            |    maybe(${indentedStrings(args, indent = 1, sorted = false)})
            |
            |    maybe(${indentedStrings(neverlinkArgs, indent = 1, sorted = false)})
        """.trimMargin()
        )
    }
}
