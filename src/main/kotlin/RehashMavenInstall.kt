package com.github.zetten.bazeldeps

import net.swiftzer.semver.SemVer
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.io.File

@CacheableTask
open class RehashMavenInstall : DefaultTask() {

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    val javaRepositoriesBzlFile: Property<File> = project.objects.property()

    // This task replaces in-place so this is technically @OutputFile as well...
    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    val mavenInstallJsonFile: Property<File> = project.objects.property()

    @Input
    val rulesJvmExternalVersion: Property<SemVer> = project.objects.property<SemVer>().convention(SemVer(4, 0))

    @TaskAction
    fun rehashMavenInstall() {
        val target = mavenInstallJsonFile.get()

        val mavenInstall = MavenInstallJson.from(target)

        mavenInstall.copy(
            dependencyTree =
            if (rulesJvmExternalVersion.get() >= SemVer(4, 3)) {
                // Parse the inputs from our java_repositories.bzl file - this could get messy if it's been modified, but it'll do for most cases
                val inputs = javaRepositoriesBzlFile.get().readText()
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
                            MavenSpec(
                                groups["group"]!!.value,
                                groups["artifact"]!!.value,
                                groups["version"]!!.value,
                                packaging = groups["packaging"]?.value,
                                classifier = groups["classifier"]?.value,
                                overrideLicenseTypes = groups["overrideLicenseTypes"]?.value?.split(", ")
                                    ?.map { s -> s.removeSurrounding("\"") },
                                neverlink = groups["neverlink"]?.value,
                                testonly = groups["testonly"]?.value,
                            )
                        }
                    }.toList()
                val repositories =
                    Regex("^\\s+\"(.*)\",$", RegexOption.MULTILINE).findAll(inputs).map { it.groupValues[1] }.toList()

                mavenInstall.dependencyTree.copy(
                    inputArtifactsHash = computeDependencyInputsSignature(
                        rulesJvmExternalVersion.get(),
                        dependencies,
                        repositories
                    ),
                    resolvedArtifactsHash = computeDependencyTreeSignature(mavenInstall.dependencyTree.dependencies)
                )
            } else if (rulesJvmExternalVersion.get() >= SemVer(4, 1)) {
                mavenInstall.dependencyTree.copy(
                    resolvedArtifactsHash = computeDependencyTreeSignature(mavenInstall.dependencyTree.dependencies)
                )
            } else {
                mavenInstall.dependencyTree.copy(
                    oldDependencyTreeSignature = computeDependencyTreeSignature(mavenInstall.dependencyTree.dependencies)
                )
            }
        ).write(target)
    }
}