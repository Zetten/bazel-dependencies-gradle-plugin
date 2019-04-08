package com.github.zetten.bazeldeps

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerConfiguration
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
open class GenerateWorkspace @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    @Input
    lateinit var dependencies: Set<ProjectDependency>

    @Input
    lateinit var repositories: List<String>

    @Input
    lateinit var licenseData: Map<ProjectDependency, List<LicenseData>>

    @Input
    var strictLicenses: Boolean = true

    @Input
    lateinit var dependenciesAttr: String

    @Input
    var safeSources: Boolean = false

    @OutputFile
    lateinit var outputFile: File

    @TaskAction
    fun generateWorkspace() {
        logger.info("Generating Bazel repository rules for {} dependencies", dependencies.size)

        val sortedDependencies = dependencies.sorted()

        val snippetFiles = sortedDependencies.map {
            val snippetFile = temporaryDir.resolve("${it.getBazelIdentifier()}.snippet")
            workerExecutor.submit(GenerateDependencySnippet::class.java, object : Action<WorkerConfiguration> {
                override fun execute(config: WorkerConfiguration) {
                    config.isolationMode = IsolationMode.NONE
                    config.params(
                            snippetFile,
                            it,
                            repositories,
                            licenseData[it],
                            strictLicenses,
                            dependenciesAttr,
                            safeSources
                    )
                }
            })
            snippetFile
        }

        outputFile.writeText("""
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
            outputFile.appendText("        omit_${it.getBazelIdentifier()} = False,\n")
        }
        outputFile.appendText("""
            |        fetch_sources = False,
            |        replacements = {}):
            |""".trimMargin())
        sortedDependencies.forEach {
            outputFile.appendText("    if not omit_${it.getBazelIdentifier()}:\n        ${it.getBazelIdentifier()}(fetch_sources, replacements)\n")
        }
        outputFile.appendText("\n")

        workerExecutor.await()

        if (snippetFiles.isEmpty()) {
            outputFile.appendText("    pass\n")
        } else {
            snippetFiles.sorted().forEachIndexed { idx, it ->
                outputFile.appendBytes(it.readBytes())
                if (idx != snippetFiles.lastIndex) outputFile.appendText("\n")
            }
        }
    }

}
