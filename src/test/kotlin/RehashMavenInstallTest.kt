package com.github.zetten.bazeldeps

import com.github.zetten.bazeldeps.TestUtils.Companion.assertOutputEqualsResource
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.exists

class RehashMavenInstallTest {

    @TempDir
    lateinit var testProjectDir: Path

    @Test
    fun `rehash with 4_0 maven_install`() {
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
            |        version = "4.0"
            |    }
            |}
            """.trimMargin()
        )

        gradle("generateRulesJvmExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        val mavenInstall = testProjectDir.resolve("build/maven_install.json")
        assertTrue(outputFile.exists())
        assertTrue(mavenInstall.exists())
        assertOutputEqualsResource(outputFile, "/rules_jvm_external.bzl")
        assertOutputEqualsResource(mavenInstall, "/rules_jvm_external 4.0.json")

        mavenInstall.resolveSibling(".maven_install.json.tmp").let { tmpMavenInstall ->
            Files.newBufferedWriter(tmpMavenInstall).use { writer ->
                var first = true
                mavenInstall.toFile().forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/repo.maven.apache.org/maven2/")) { "https${it.groupValues[1]}/nexus.observing.earth/repository/maven-central/" })
                }
            }
            Files.copy(tmpMavenInstall, mavenInstall, REPLACE_EXISTING)
            Files.delete(tmpMavenInstall)
        }

        gradle("rehashMavenInstall")

        assertOutputEqualsResource(mavenInstall, "/rules_jvm_external 4.0 rehashed.json")
    }

    @Test
    fun `rehash with 4_1 maven_install`() {
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
            |        version = "4.1"
            |    }
            |}
            """.trimMargin()
        )

        gradle("generateRulesJvmExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        val mavenInstall = testProjectDir.resolve("build/maven_install.json")
        assertTrue(outputFile.exists())
        assertTrue(mavenInstall.exists())
        assertOutputEqualsResource(outputFile, "/rules_jvm_external.bzl")
        assertOutputEqualsResource(mavenInstall, "/rules_jvm_external 4.1.json")

        mavenInstall.resolveSibling(".maven_install.json.tmp").let { tmpMavenInstall ->
            Files.newBufferedWriter(tmpMavenInstall).use { writer ->
                var first = true
                mavenInstall.toFile().forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/repo.maven.apache.org/maven2/")) { "https${it.groupValues[1]}/nexus.observing.earth/repository/maven-central/" })
                }
            }
            Files.copy(tmpMavenInstall, mavenInstall, REPLACE_EXISTING)
            Files.delete(tmpMavenInstall)
        }

        gradle("rehashMavenInstall")

        assertOutputEqualsResource(mavenInstall, "/rules_jvm_external 4.1 rehashed.json")
    }

    @Test
    fun `rehash with 4_3 maven_install`() {
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
            |        version = "4.3"
            |    }
            |}
            """.trimMargin()
        )

        gradle("generateRulesJvmExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        val mavenInstall = testProjectDir.resolve("build/maven_install.json")
        assertTrue(outputFile.exists())
        assertTrue(mavenInstall.exists())
        assertOutputEqualsResource(outputFile, "/rules_jvm_external.bzl")
        assertOutputEqualsResource(mavenInstall, "/rules_jvm_external 4.3.json")

        val replace = "repo.maven.apache.org/maven2"
        val with = "nexus.observing.earth/repository/maven-central"
        replaceUrlsInFile(outputFile, replace, with)
        replaceUrlsInFile(mavenInstall, replace, with)

        gradle("rehashMavenInstall")

        assertOutputEqualsResource(mavenInstall, "/rules_jvm_external 4.3 rehashed.json")
    }

    @Test
    fun `rehash with 5_0 maven_install`() {
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
            |        version = "5.0"
            |    }
            |}
            """.trimMargin()
        )

        gradle("generateRulesJvmExternal")

        val outputFile = testProjectDir.resolve("build/java_repositories.bzl")
        val mavenInstall = testProjectDir.resolve("build/maven_install.json")
        assertTrue(outputFile.exists())
        assertTrue(mavenInstall.exists())
        assertOutputEqualsResource(outputFile, "/rules_jvm_external.bzl")
        assertOutputEqualsResource(mavenInstall, "/rules_jvm_external 5.0.json")

        val replace = "repo.maven.apache.org/maven2"
        val with = "nexus.observing.earth/repository/maven-central"
        replaceUrlsInFile(outputFile, replace, with)
        replaceUrlsInFile(mavenInstall, replace, with)

        gradle("rehashMavenInstall")

        assertOutputEqualsResource(mavenInstall, "/rules_jvm_external 5.0 rehashed.json")
    }

    private fun replaceUrlsInFile(file: Path, replace: String, with: String) {
        file.resolveSibling(".${file.fileName}.tmp").let { tmpFile ->
            Files.newBufferedWriter(tmpFile).use { writer ->
                var first = true
                file.toFile().forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/${replace}")) { "https${it.groupValues[1]}/$with" })
                }
            }
            Files.copy(tmpFile, file, REPLACE_EXISTING)
            Files.delete(tmpFile)
        }
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