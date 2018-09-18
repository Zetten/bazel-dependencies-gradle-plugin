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

    @OutputFile
    lateinit var outputFile: File

    @TaskAction
    fun generateWorkspace() {
        logger.info("Generating Bazel repository rules for {} dependencies", dependencies.size)

        val snippetFiles = dependencies.map {
            val snippetFile = temporaryDir.resolve("${it.getBazelIdentifier()}.snippet")
            workerExecutor.submit(GenerateDependencySnippet::class.java, object : Action<WorkerConfiguration> {
                override fun execute(config: WorkerConfiguration) {
                    config.isolationMode = IsolationMode.NONE
                    config.params(snippetFile, it, repositories, licenseData[it], strictLicenses)
                }
            })
            snippetFile
        }

        workerExecutor.await()

        outputFile.writeText("""load("@bazel_tools//tools/build_defs/repo:java.bzl", "java_import_external")

def _replace_dependencies(dependencies, replacements):
    new_dependencies = depset()
    for dep in dependencies:
        if dep in replacements.keys():
            new_dependencies = depset(transitive = [new_dependencies, depset(direct = replacements.get(dep))])
        else:
            new_dependencies = depset(transitive = [new_dependencies, depset(direct = [dep])])
    return new_dependencies.to_list()

def java_repositories(excludes = [], replacements = {}):
""")

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
