package com.github.zetten.bazeldeps

import net.swiftzer.semver.SemVer
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property

@CacheableTask
open class RehashMavenInstall : DefaultTask() {

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    val javaRepositoriesBzlFile: RegularFileProperty = project.objects.fileProperty()

    // This task replaces in-place so this is technically @OutputFile as well...
    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    val mavenInstallJsonFile: RegularFileProperty = project.objects.fileProperty()

    @Input
    val rulesJvmExternalVersion: Property<SemVer> = project.objects.property()

    @TaskAction
    fun rehashMavenInstall() {
        val target = mavenInstallJsonFile.get().asFile

        val mavenInstallContents: Any =
            if (rulesJvmExternalVersion.get() >= SemVer(5, 0)) {
                updateMavenInstallJsonV2(MavenInstallJsonV2Contents.from(target))
            } else {
                updateMavenInstallJsonV1(MavenInstallJsonV1Contents.from(target))
            }

        objectMapper.writer(MavenInstallPrettyPrinter())
            .writeValue(mavenInstallJsonFile.get().asFile, mavenInstallContents)
    }

    //
    private fun updateMavenInstallJsonV2(it: MavenInstallJsonV2Contents): MavenInstallJsonV2Contents {
        val javaRepositoriesBzl = parseJavaRepositoriesBzl()

        return it.copy(
            inputArtifactsHash = computeDependencyInputsSignature(
                rulesJvmExternalVersion.get(),
                javaRepositoriesBzl.dependencies,
                javaRepositoriesBzl.repositories
            ),
            resolvedArtifactsHash = it.computeResolvedArtifactsHash()
        )
    }

    private fun updateMavenInstallJsonV1(it: MavenInstallJsonV1Contents) = it.copy(
        dependencyTree = if (rulesJvmExternalVersion.get() >= SemVer(4, 3)) {
            val javaRepositoriesBzl = parseJavaRepositoriesBzl()
            it.dependencyTree.copy(
                inputArtifactsHash = computeDependencyInputsSignature(
                    rulesJvmExternalVersion.get(),
                    javaRepositoriesBzl.dependencies,
                    javaRepositoriesBzl.repositories
                ),
                resolvedArtifactsHash = computeDependencyTreeSignature(it.dependencyTree.dependencies)
            )
        } else if (rulesJvmExternalVersion.get() >= SemVer(4, 1)) {
            it.dependencyTree.copy(resolvedArtifactsHash = computeDependencyTreeSignature(it.dependencyTree.dependencies))
        } else {
            it.dependencyTree.copy(oldDependencyTreeSignature = computeDependencyTreeSignature(it.dependencyTree.dependencies))
        }
    )

    private fun parseJavaRepositoriesBzl(): JavaRepositoriesBzl {
        // Parse the inputs from our java_repositories.bzl file - this could get messy if it's been modified, but it'll do for most cases
        val inputs = javaRepositoriesBzlFile.get().asFile.readText()
        val dependencies =
            Regex(
                "^\\s+maven.artifact\\(" +
                        "\"(?<group>[^\"]*)\", " +
                        "\"(?<artifact>[^\"]*)\", " +
                        "\"(?<version>[^\"]*)\"" +
                        "(, packaging = \"(?<packaging>[^\"]*)\")?" +
                        "(, classifier = \"(?<classifier>[^\"]*)\")?" +
                        "(, override_license_types = \\[(?<overrideLicenseTypes>[^]]*)])?" +
                        "(, exclusions = \\[(?<exclusions>.*)])?" +
                        "(, neverlink = (?<neverlink>\\w*))?" +
                        "(, testonly = (?<testonly>\\w*))?" +
                        "\\),$", RegexOption.MULTILINE
            ).findAll(inputs).map {
                (it.groups as MatchNamedGroupCollection).let { groups ->
                    ProjectDependency(
                        ArtifactIdentifier(
                            groups["group"]!!.value,
                            groups["artifact"]!!.value,
                            groups["version"]!!.value,
                            packaging = groups["packaging"]?.value ?: "jar",
                            classifier = groups["classifier"]?.value
                        ),
                        dependencies = emptySet(),
                        runtimeOnlyDependencies = emptySet(),
                        compileOnlyDependencies = emptySet(),
                        exclusions = groups["exclusions"]?.value?.let { exclusions ->
                            Regex("^(?:\"(.*)\",?)*$").findAll(exclusions)
                                .map { exclusion ->
                                    exclusion.groups[1]!!.value.split(":")
                                        .let { e -> ProjectDependencyExclusion(e[0], e[1]) }
                                }.toSet()
                        } ?: emptySet(),
                        neverlink = groups["neverlink"]?.value == "True",
                        testonly = groups["testonly"]?.value == "True",
                    )
                }
            }.toList()
        val repositories =
            Regex("REPOSITORIES = \\[\\s*\"(.*)\"(,\\s+)?]", RegexOption.MULTILINE)
                .findAll(inputs).map { it.groupValues[1] }.toList()

        return JavaRepositoriesBzl(dependencies, repositories)
    }
}

data class JavaRepositoriesBzl(
    val dependencies: List<ProjectDependency>,
    val repositories: List<String>
)