package com.github.zetten.bazeldeps

import com.github.zetten.bazeldeps.TestUtils.Companion.assertOutputEqualsResource
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

class GenerateRulesJvmExternalTest {

    @TempDir
    lateinit var testProjectDir: Path

    @Test
    fun `rules_jvm_external without maven_install`() {
        givenBuildScript(
            """
            |plugins {
            |    `java-base`
            |    id("com.github.zetten.bazel-dependencies-plugin")
            |}
            |
            |repositories {
            |    mavenCentral()
            |}
            |
            |val generate by configurations.creating {
            |    attributes {
            |        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            |    }
            |}
            |
            |dependencies {
            |    generate(enforcedPlatform("io.grpc:grpc-bom:1.61.0"))
            |    generate("io.grpc:grpc-core") {
            |        exclude("io.grpc", "grpc-util")
            |    }
            |    generate("com.google.guava:guava:33.0.0-jre")
            |}
            |
            |bazelDependencies {
            |    configuration = generate
            |    outputFile = project.layout.buildDirectory.file("java_repositories.bzl")
            |    rulesJvmExternal {
            |        createMavenInstallJson = false
            |    }
            |}
            """.trimMargin()
        )

        gradle("generateRulesJvmExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        val mavenInstall = testProjectDir.resolve("build/maven_install.json")
        assertTrue(outputFile.exists())
        assertFalse(mavenInstall.exists())
        assertOutputEqualsResource(outputFile, "/rules_jvm_external.bzl")
    }

    @Test
    fun `rules_jvm_external with testonly and neverlink`() {
        givenBuildScript(
            """
            |plugins {
            |    `java-base`
            |    id("com.github.zetten.bazel-dependencies-plugin")
            |}
            |
            |repositories {
            |    mavenCentral()
            |}
            |
            |val generate by configurations.creating {
            |    attributes {
            |        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            |    }
            |}
            |
            |dependencies {
            |    generate(enforcedPlatform("io.grpc:grpc-bom:1.61.0"))
            |    generate("io.grpc:grpc-core") {
            |        exclude("io.grpc", "grpc-util")
            |    }
            |    generate("com.google.guava:guava:33.0.0-jre")
            |}
            |
            |bazelDependencies {
            |    configuration = generate
            |    outputFile = project.layout.buildDirectory.file("java_repositories.bzl")
            |    neverlink = setOf("com.google.guava:guava")
            |    testonly = setOf("com.google.guava:guava")
            |}
            """.trimMargin()
        )

        gradle("generateRulesJvmExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        val mavenInstall = testProjectDir.resolve("build/maven_install.json")
        assertTrue(outputFile.exists())
        assertTrue(mavenInstall.exists())
        assertOutputEqualsResource(outputFile, "/rules_jvm_external with testonly and neverlink.bzl")
    }

    private fun gradle(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .forwardStdOutput(System.out.bufferedWriter())
            .forwardStdError(System.err.bufferedWriter())
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments(*arguments)
            .build()

    private fun givenBuildScript(script: String) =
        testProjectDir.resolve("build.gradle.kts").toFile().apply {
            writeText(script)
        }

}