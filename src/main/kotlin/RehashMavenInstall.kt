package com.github.zetten.bazeldeps

import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
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
    // This task replaces in-place so this is technically @OutputFile as well...
    val mavenInstallJsonFile: Property<File> = project.objects.property()

    @UseExperimental(UnstableDefault::class)
    @TaskAction
    fun rehashMavenInstall() {
        val target = mavenInstallJsonFile.get()

        val mavenInstall = Json.parse(MavenInstallJson.serializer(), target.readText())

        target.writeText(
            Json(JsonConfiguration.Stable.copy(prettyPrint = true)).stringify(
                MavenInstallJson.serializer(),
                mavenInstall.copy(
                    dependencyTree = mavenInstall.dependencyTree.copy(
                        dependencyTreeSignature = computeDependencyTreeSignature(mavenInstall.dependencyTree.dependencies)
                    )
                )
            )
        )
    }
}