package com.github.zetten.bazeldeps

import com.github.zetten.bazeldeps.TestUtils.Companion.assertOutputEqualsResource
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

class GenerateJvmImportExternalTest {

    @TempDir
    lateinit var testProjectDir: Path

    @Test
    fun `jvm_import_external with aggregator repo`() {
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
            |    generate("com.google.guava:guava:33.0.0-jre")
            |}
            |
            |bazelDependencies {
            |    configuration = generate
            |    outputFile = project.layout.buildDirectory.file("java_repositories.bzl")
            |}
            """.trimMargin()
        )

        gradle("generateJvmImportExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        assertTrue(outputFile.exists())
        assertOutputEqualsResource(outputFile, "/jvm_import_external with aggregator repo.bzl")
    }

    @Test
    fun `jvm_import_external without aggregator repo`() {
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
            |    generate("com.google.guava:guava:33.0.0-jre")
            |}
            |
            |bazelDependencies {
            |    configuration = generate
            |    outputFile = project.layout.buildDirectory.file("java_repositories.bzl")
            |    jvmImportExternal {
            |        createAggregatorRepo = false
            |    }
            |}
            """.trimMargin()
        )

        gradle("generateJvmImportExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        assertTrue(outputFile.exists())
        assertOutputEqualsResource(outputFile, "/jvm_import_external without aggregator repo.bzl")
    }

    @Test
    fun `jvm_import_external with testonly and neverlink`() {
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
            |    generate("com.google.guava:guava:33.0.0-jre")
            |}
            |
            |bazelDependencies {
            |    configuration = generate
            |    outputFile = project.layout.buildDirectory.file("java_repositories.bzl")
            |    neverlink = setOf("com.google.guava:guava")
            |    testonly = setOf("com.google.guava:guava")
            |    jvmImportExternal {
            |        createAggregatorRepo = false
            |    }
            |}
            """.trimMargin()
        )

        gradle("generateJvmImportExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        assertTrue(outputFile.exists())
        assertOutputEqualsResource(outputFile, "/jvm_import_external with testonly and neverlink.bzl")
    }

    @Test
    fun `jvm_import_external with exclusion`() {
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
            |    generate("io.grpc:grpc-core:1.61.0") {
            |        exclude("io.grpc", "grpc-util")
            |    }
            |    generate("com.google.guava:guava:33.0.0-jre")
            |}
            |
            |bazelDependencies {
            |    configuration = generate
            |    outputFile = project.layout.buildDirectory.file("java_repositories.bzl")
            |    jvmImportExternal {
            |        createAggregatorRepo = false
            |    }
            |}
            """.trimMargin()
        )

        gradle("generateJvmImportExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        assertTrue(outputFile.exists())
        assertOutputEqualsResource(outputFile, "/jvm_import_external with exclusion.bzl")
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