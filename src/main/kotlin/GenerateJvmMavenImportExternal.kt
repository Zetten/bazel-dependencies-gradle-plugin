package com.github.zetten.bazeldeps

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerConfiguration
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
open class GenerateJvmMavenImportExternal @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

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

        val snippetFiles = sortedDependencies.map {
            val snippetFile = temporaryDir.resolve("${it.getBazelIdentifier()}.snippet")
            workerExecutor.submit(GenerateDependencySnippet::class.java, object : Action<WorkerConfiguration> {
                override fun execute(config: WorkerConfiguration) {
                    config.isolationMode = IsolationMode.NONE
                    config.params(
                            snippetFile,
                            it,
                            repositories.get(),
                            licenseData.get()[it],
                            strictLicenses.get(),
                            dependenciesAttr.get(),
                            safeSources.get()
                    )
                }
            })
            snippetFile
        }

        output.writeText("""
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
            |""".trimMargin())
        sortedDependencies.forEach {
            output.appendText("        omit_${it.getBazelIdentifier()} = False,\n")
        }
        output.appendText("""
            |        fetch_sources = False,
            |        replacements = {}):
            |""".trimMargin())
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
