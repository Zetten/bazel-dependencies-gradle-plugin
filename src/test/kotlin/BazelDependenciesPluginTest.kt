package com.github.zetten.bazeldeps

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class BazelDependenciesPluginTest {

    val objectMapper = ObjectMapper()

    @TempDir
    @JvmField
    var temporaryFolder: Path? = null

    @Test
    fun `strict license resolution causes an error`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                strictLicenses.set(true)
            }
        """.trimIndent()
        )

        try {
            build("generateJvmMavenImportExternal").output.trimEnd()
            fail("Expected UnexpectedBuildFailure")
        } catch (e: UnexpectedBuildFailure) {
            assertThat(e).hasMessageThat().contains("Could not determine a license for dom4j:dom4j:1.6.1")
        }
    }

    @Test
    fun `per-dependency licenses can be set and overridden and sources checked`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:27.1-jre") // imports com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava which has no srcjar, i.e. `fetch_sources = False`
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                licenseOverrides.set(mapOf(
                    Pair("dom4j:dom4j:1.6.1", "notice"), // https://github.com/dom4j/dom4j/blob/master/LICENSE
                    Pair("com.google.code.findbugs:jsr305:3.0.2", "restricted") // overrides "notice"
                ))
                safeSources.set(true)
                sourcesChecksums.set(true)
            }
        """.trimIndent()
        )

        build("generateJvmMavenImportExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_license_overrides.bzl")!!
                .readText()
        )
    }

    @Test
    fun `non-strict license mode can be used and dependencies attribute changed`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                strictLicenses.set(false)
                dependenciesAttr.set("runtime_deps")
            }
        """.trimIndent()
        )

        build("generateJvmMavenImportExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_non_strict.bzl")!!
                .readText()
        )
    }

    @Test
    fun `simple rules_jvm_external attributes can be generated`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                createMavenInstallJson.set(false)
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external.bzl")!!
                .readText()
        )
    }

    @Test
    fun `rules_jvm_external attributes can be configured`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
                generate("junit:junit:4.12")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                createMavenInstallJson.set(false)
                compileOnly.set(setOf(
                    "com.google.errorprone:error_prone_annotations:2.1.3",
                    "com.google.j2objc:j2objc-annotations:1.1",
                    "org.codehaus.mojo:animal-sniffer-annotations" // test lenient dependency matching
                ))
                testOnly.set(setOf("junit:junit:4.12"))
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external_configured.bzl")!!
                .readText()
        )
    }

    @Test
    fun `rules_jvm_external dependencies are pinned by default`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external.bzl")!!
                .readText()
        )
        val mavenInstall = temporaryFolder!!.resolve("build/maven_install.json")
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install.json")!!
                    .readText()
            )
        )
    }

    @Test
    fun `rules_jvm_external 4_1 maven_install is supported`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                rulesJvmExternalVersion.set("4.1.0")
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external.bzl")!!
                .readText()
        )
        val mavenInstall = temporaryFolder!!.resolve("build/maven_install.json")
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install_4.1_format.json")!!
                    .readText()
            )
        )
    }

    @Test
    fun `rules_jvm_external 4_3 maven_install is supported`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
                generate("junit:junit:4.12")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                rulesJvmExternalVersion.set("4.3.0")
                sourcesChecksums.set(true)
                compileOnly.set(setOf(
                    "com.google.errorprone:error_prone_annotations:2.1.3",
                    "com.google.j2objc:j2objc-annotations:1.1",
                    "org.codehaus.mojo:animal-sniffer-annotations:1.14"
                ))
                testOnly.set(setOf("junit:junit:4.12"))
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external_configured.bzl")!!
                .readText()
        )
        val mavenInstall = temporaryFolder!!.resolve("build/maven_install.json")
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install_4.3_format.json")!!
                    .readText()
            )
        )
    }

    @Test
    fun `rules_jvm_external 5_0 maven_install is supported`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
                generate("junit:junit:4.12")
                generate("io.netty:netty-codec:4.1.90.Final")
                generate("io.netty:netty-codec-dns:4.1.90.Final")
                generate("io.netty:netty-codec-http:4.1.90.Final")
                generate("io.netty:netty-codec-socks:4.1.90.Final")
                generate("io.netty:netty-common:4.1.90.Final")
                generate("io.netty:netty-handler:4.1.90.Final")
                generate("io.netty:netty-handler-proxy:4.1.90.Final")
                generate("io.netty:netty-resolver:4.1.90.Final")
                generate("io.netty:netty-resolver-dns:4.1.90.Final")
                generate("io.netty:netty-resolver-dns-classes-macos:4.1.90.Final")
                generate("io.netty:netty-resolver-dns-native-macos:4.1.90.Final:osx-x86_64")
                generate("io.netty:netty-transport:4.1.90.Final")
                generate("io.netty:netty-transport-classes-epoll:4.1.90.Final")
                generate("io.netty:netty-transport-native-epoll:4.1.90.Final:linux-x86_64")
                generate("io.netty:netty-transport-native-unix-common:4.1.90.Final")
                generate("io.projectreactor.netty:reactor-netty-core:1.1.5")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                rulesJvmExternalVersion.set("5.0")
                sourcesChecksums.set(true)
                compileOnly.set(setOf(
                    "com.google.errorprone:error_prone_annotations:2.1.3",
                    "com.google.j2objc:j2objc-annotations:1.1",
                    "org.codehaus.mojo:animal-sniffer-annotations:1.14"
                ))
                testOnly.set(setOf("junit:junit:4.12"))
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal", "--info")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external_configured_with_classifiers.bzl")!!
                .readText()
        )
        val mavenInstall = temporaryFolder!!.resolve("build/maven_install.json")
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install_5.0_format.json")!!
                    .readText()
            )
        )
    }

    @Test
    fun `rules_jvm_external maven_install may be rehashed`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external.bzl")!!
                .readText()
        )
        val mavenInstall = temporaryFolder!!.resolve("build/maven_install.json")
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install.json")!!
                    .readText()
            )
        )

        mavenInstall.resolveSibling(".maven_install.json.tmp").let { tmpMavenInstall ->
            Files.newBufferedWriter(tmpMavenInstall).use { writer ->
                var first = true
                mavenInstall.toFile().forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/repo.maven.apache.org/maven2/")) { "http${it.groupValues[1]}/customrepo.example.com/context/" })
                }
            }
            Files.copy(tmpMavenInstall, mavenInstall, StandardCopyOption.REPLACE_EXISTING)
            Files.delete(tmpMavenInstall)
        }

        build("rehashMavenInstall")
        assertThat(Files.exists(mavenInstall)).isTrue()
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/rehashed_maven_install.json")!!
                    .readText()
            )
        )
    }

    @Test
    fun `rules_jvm_external 4_1 maven_install may be rehashed`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                rulesJvmExternalVersion.set("4.1.0")
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external.bzl")!!
                .readText()
        )
        val mavenInstall = temporaryFolder!!.resolve("build/maven_install.json")
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install_4.1_format.json")!!
                    .readText()
            )
        )

        mavenInstall.resolveSibling(".maven_install.json.tmp").let { tmpMavenInstall ->
            Files.newBufferedWriter(tmpMavenInstall).use { writer ->
                var first = true
                mavenInstall.toFile().forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/repo.maven.apache.org/maven2/")) { "http${it.groupValues[1]}/customrepo.example.com/context/" })
                }
            }
            Files.copy(tmpMavenInstall, mavenInstall, StandardCopyOption.REPLACE_EXISTING)
            Files.delete(tmpMavenInstall)
        }

        build("rehashMavenInstall")
        assertThat(Files.exists(mavenInstall)).isTrue()
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/rehashed_maven_install_4.1_format.json")!!
                    .readText()
            )
        )
    }

    @Test
    fun `rules_jvm_external 4_3 maven_install may be rehashed`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
                generate("junit:junit:4.12")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                rulesJvmExternalVersion.set("4.3.0")
                sourcesChecksums.set(true)
                compileOnly.set(setOf(
                    "com.google.errorprone:error_prone_annotations:2.1.3",
                    "com.google.j2objc:j2objc-annotations:1.1",
                    "org.codehaus.mojo:animal-sniffer-annotations:1.14"
                ))
                testOnly.set(setOf("junit:junit:4.12"))
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external_configured.bzl")!!
                .readText()
        )
        val mavenInstall = temporaryFolder!!.resolve("build/maven_install.json")
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install_4.3_format.json")!!
                    .readText()
            )
        )


        outputFile.resolveSibling(".java_repositories.bzl.tmp").let { tmpJavaRepositories ->
            Files.newBufferedWriter(tmpJavaRepositories).use { writer ->
                var first = true
                outputFile.toFile().forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/repo.maven.apache.org/maven2/")) { "https${it.groupValues[1]}/example.com/repository/maven-central/" })
                }
            }
            Files.copy(tmpJavaRepositories, outputFile, StandardCopyOption.REPLACE_EXISTING)
            Files.delete(tmpJavaRepositories)
        }
        mavenInstall.resolveSibling(".maven_install.json.tmp").let { tmpMavenInstall ->
            Files.newBufferedWriter(tmpMavenInstall).use { writer ->
                var first = true
                mavenInstall.toFile().forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/repo.maven.apache.org/maven2/")) { "https${it.groupValues[1]}/example.com/repository/maven-central/" })
                }
            }
            Files.copy(tmpMavenInstall, mavenInstall, StandardCopyOption.REPLACE_EXISTING)
            Files.delete(tmpMavenInstall)
        }

        build("rehashMavenInstall")
        assertThat(Files.exists(mavenInstall)).isTrue()
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/rehashed_maven_install_4.3_format.json")!!
                    .readText()
            )
        )
    }

    @Test
    fun `rules_jvm_external 5_0 maven_install may be rehashed`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                mavenCentral()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
                generate("junit:junit:4.12")
                generate("io.netty:netty-codec:4.1.90.Final")
                generate("io.netty:netty-codec-dns:4.1.90.Final")
                generate("io.netty:netty-codec-http:4.1.90.Final")
                generate("io.netty:netty-codec-socks:4.1.90.Final")
                generate("io.netty:netty-common:4.1.90.Final")
                generate("io.netty:netty-handler:4.1.90.Final")
                generate("io.netty:netty-handler-proxy:4.1.90.Final")
                generate("io.netty:netty-resolver:4.1.90.Final")
                generate("io.netty:netty-resolver-dns:4.1.90.Final")
                generate("io.netty:netty-resolver-dns-classes-macos:4.1.90.Final")
                generate("io.netty:netty-resolver-dns-native-macos:4.1.90.Final:osx-x86_64")
                generate("io.netty:netty-transport:4.1.90.Final")
                generate("io.netty:netty-transport-classes-epoll:4.1.90.Final")
                generate("io.netty:netty-transport-native-epoll:4.1.90.Final:linux-x86_64")
                generate("io.netty:netty-transport-native-unix-common:4.1.90.Final")
                generate("io.projectreactor.netty:reactor-netty-core:1.1.5")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                rulesJvmExternalVersion.set("5.0.0")
                sourcesChecksums.set(true)
                compileOnly.set(setOf(
                    "com.google.errorprone:error_prone_annotations:2.1.3",
                    "com.google.j2objc:j2objc-annotations:1.1",
                    "org.codehaus.mojo:animal-sniffer-annotations:1.14"
                ))
                testOnly.set(setOf("junit:junit:4.12"))
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal", "--info")

        val outputFile = temporaryFolder!!.resolve("build/java_repositories.bzl")
        assertThat(Files.exists(outputFile)).isTrue()
        assertThat(Files.readString(outputFile)).isEqualTo(
            BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external_configured_with_classifiers.bzl")!!
                .readText()
        )
        val mavenInstall = temporaryFolder!!.resolve("build/maven_install.json")
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install_5.0_format.json")!!
                    .readText()
            )
        )

        outputFile.resolveSibling(".java_repositories.bzl.tmp").let { tmpJavaRepositories ->
            Files.newBufferedWriter(tmpJavaRepositories).use { writer ->
                var first = true
                outputFile.toFile().forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/repo.maven.apache.org/maven2/")) { "https${it.groupValues[1]}/example.com/repository/maven-central/" })
                }
            }
            Files.copy(tmpJavaRepositories, outputFile, StandardCopyOption.REPLACE_EXISTING)
            Files.delete(tmpJavaRepositories)
        }
        mavenInstall.resolveSibling(".maven_install.json.tmp").let { tmpMavenInstall ->
            Files.newBufferedWriter(tmpMavenInstall).use { writer ->
                var first = true
                mavenInstall.toFile().forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/repo.maven.apache.org/maven2/")) { "https${it.groupValues[1]}/example.com/repository/maven-central/" })
                }
            }
            Files.copy(tmpMavenInstall, mavenInstall, StandardCopyOption.REPLACE_EXISTING)
            Files.delete(tmpMavenInstall)
        }

        build("rehashMavenInstall")
        assertThat(Files.exists(mavenInstall)).isTrue()
        assertThat(objectMapper.readTree(Files.readString(mavenInstall))).isEqualTo(
            objectMapper.readTree(
                BazelDependenciesPluginTest::class.java.getResource("/rehashed_maven_install_5.0_format.json")!!
                    .readText()
            )
        )
    }

    private fun build(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .forwardStdOutput(System.out.bufferedWriter())
            .forwardStdError(System.err.bufferedWriter())
            .withProjectDir(temporaryFolder!!.toFile())
            .withPluginClasspath()
            .withArguments(*arguments)
            .build()

    private fun givenBuildScript(script: String) =
        newFile("build.gradle.kts").apply {
            writeText(script)
        }

    private fun givenGradleProperties(script: String) =
        newFile("gradle.properties").apply {
            writeText(script)
        }

    private fun newFile(fileName: String): File =
        temporaryFolder!!.resolve(fileName).toFile()
}