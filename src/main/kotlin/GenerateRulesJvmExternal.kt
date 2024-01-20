package com.github.zetten.bazeldeps

import net.swiftzer.semver.SemVer
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
open class GenerateRulesJvmExternal @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    @Input
    val dependencies: SetProperty<ProjectDependency> = project.objects.setProperty()

    @Input
    val repositories: ListProperty<String> = project.objects.listProperty()

    @Input
    val projectExclusions: SetProperty<ProjectDependencyExclusion> = project.objects.setProperty()

    @Input
    val rulesJvmExternalVersion: Property<SemVer> = project.objects.property()

    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @OutputFile
    @Optional
    val mavenInstallJsonFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun generateWorkspace() {
        val sortedDependencies = dependencies.get().map {
            // TODO rules_jvm_external doesn't support dependency scoping, so merge it all together
            it.copy(
                dependencies = (it.dependencies + it.runtimeOnlyDependencies + it.compileOnlyDependencies).toSet(),
                runtimeOnlyDependencies = emptySet(),
                compileOnlyDependencies = emptySet()
            )
        }.sorted()
        val sortedRepositories = repositories.get().sorted()
        val sortedExclusions = projectExclusions.get().map { "${it.group}:${it.artifact}" }.sorted()

        logger.warn("Generating Bazel rules_jvm_external properties for {} dependencies", dependencies.get().size)

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()

        output.writeText(
            """
            |load("@rules_jvm_external//:specs.bzl", "maven")
            |
            |REPOSITORIES = [${indentedStrings(sortedRepositories.map { "\"${it}\"" }, indent = 0)}]
            |
            |ARTIFACTS = [${indentedStrings(sortedDependencies.map { artifactsEntry(it) }, indent = 0)}]
            |
            |EXCLUSIONS = [${indentedStrings(sortedExclusions.map { "\"${it}\"" }, indent = 0)}]
            |""".trimMargin()
        )

        if (mavenInstallJsonFile.isPresent) {
            val contents: Any

            if (rulesJvmExternalVersion.get() >= SemVer(5, 0)) {
                contents = MavenInstallJsonV2(
                    workerExecutor,
                    rulesJvmExternalVersion,
                    temporaryDir,
                    sortedDependencies,
                    sortedRepositories
                ).contents
            } else {
                contents = MavenInstallJsonV1(
                    workerExecutor,
                    rulesJvmExternalVersion,
                    temporaryDir,
                    sortedDependencies,
                    sortedRepositories
                ).contents
            }

            objectMapper.writer(MavenInstallPrettyPrinter()).writeValue(mavenInstallJsonFile.get().asFile, contents)
        }
    }

    private fun artifactsEntry(dep: ProjectDependency): String {
        val params = buildList {
            add("\"${dep.id.group}\"")
            add("\"${dep.id.name}\"")
            add("\"${dep.id.version}\"")
            if (dep.id.packaging != "jar") add("packaging = \"${dep.id.packaging}\"")
            if (dep.id.classifier != null) add("classifier = \"${dep.id.classifier}\"")
            if (dep.neverlink) add("neverlink = True")
            if (dep.testonly) add("testonly = True")
            if (dep.exclusions.isNotEmpty()) {
                val exclusions = dep.exclusions.map { "\"${it.group}:${it.artifact}\"" }
                add("exclusions = [${exclusions.joinToString(", ")}]")
            }
        }
        return "maven.artifact(${params.joinToString(", ")})"
    }

}
