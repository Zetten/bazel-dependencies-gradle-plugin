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
            if (rulesJvmExternalVersion.get() < SemVer(4, 1)) {
                mavenInstall.dependencyTree.copy(
                    oldDependencyTreeSignature = computeDependencyTreeSignature(mavenInstall.dependencyTree.dependencies)
                )
            } else {
                mavenInstall.dependencyTree.copy(
                    resolvedArtifactsHash = computeDependencyTreeSignature(mavenInstall.dependencyTree.dependencies)
                )
            }
        ).write(target)
    }
}