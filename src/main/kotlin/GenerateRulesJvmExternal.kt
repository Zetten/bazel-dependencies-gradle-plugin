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
            |load("@rules_jvm_external//:specs.bzl", "maven")
            |
            |REPOSITORIES = [
            |${repositories.sorted().joinToString("\n") { "    \"$it\"," }}
            |]
            |
            |ARTIFACTS = [
            |${dependencies.sorted().joinToString("\n") { artifactBlock(it) }}
            |]
            |""".trimMargin()
        )
    }

    private fun artifactBlock(dep: ProjectDependency): String {
        val params = mutableListOf("\"${dep.id.group}\"", "\"${dep.id.name}\"", "\"${dep.id.version}\"")
        if (dep.jar!!.extension != "jar") params += "packaging = \"${dep.jar.extension}\""
        if (dep.classifier != null) params += "classifier = \"${dep.classifier}\""
        if (dep.overrideLicenseTypes != null) params += "override_license_types = [${dep.overrideLicenseTypes.sorted().joinToString(
            ", "
        ) { "\"$it\"" }}]"
        if (dep.neverlink) params += "neverlink = True"
        if (dep.testonly) params += "testonly = True"

        return """
        |    maven.artifact(${params.joinToString(", ")}),
        """.trimMargin()
    }

}
