package com.github.zetten.bazeldeps

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
open class GenerateRulesJvmExternal : DefaultTask() {

    @Input
    lateinit var dependencies: Set<ProjectDependency>

    @Input
    lateinit var repositories: List<String>

    @OutputFile
    lateinit var outputFile: File

    @TaskAction
    fun generateWorkspace() {
        logger.info("Generating Bazel rules_jvm_external attributes for {} dependencies", dependencies.size)

        outputFile.writeText(
            """
            |REPOSITORIES = [
            |${repositories.sorted().joinToString("\n") { "    \"$it\"," }}
            |]
            |
            |ARTIFACTS = [
            |${dependencies.sorted().joinToString("\n") {"    \"${it.getJvmMavenImportExternalCoordinates()}\"," }}
            |]
            |""".trimMargin()
        )
    }

}
