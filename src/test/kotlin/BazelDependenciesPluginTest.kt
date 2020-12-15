import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import java.io.File

class BazelDependenciesPluginTest {

    @Rule
    @JvmField
    var ex: ExpectedException = ExpectedException.none()

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `strict license resolution causes an error`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                jcenter()
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

        ex.expectMessage("Could not determine a license for dom4j:dom4j:1.6.1")
        build("generateJvmMavenImportExternal").output.trimEnd()
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
                jcenter()
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

        val outputFile = temporaryFolder.root.resolve("build/java_repositories.bzl")
        assertThat(outputFile.exists(), equalTo(true))
        assertThat(
            outputFile.readText(),
            equalTo(
                BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_license_overrides.bzl")
                    .readText()
            )
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
                jcenter()
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

        val outputFile = temporaryFolder.root.resolve("build/java_repositories.bzl")
        assertThat(outputFile.exists(), equalTo(true))
        assertThat(
            outputFile.readText(),
            equalTo(
                BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_non_strict.bzl")
                    .readText()
            )
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
                jcenter()
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

        val outputFile = temporaryFolder.root.resolve("build/java_repositories.bzl")
        assertThat(outputFile.exists(), equalTo(true))
        assertThat(
            outputFile.readText(),
            equalTo(
                BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external.bzl")
                    .readText()
            )
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
                jcenter()
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

        val outputFile = temporaryFolder.root.resolve("build/java_repositories.bzl")
        assertThat(outputFile.exists(), equalTo(true))
        assertThat(
            outputFile.readText(),
            equalTo(
                BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external_configured.bzl")
                    .readText()
            )
        )
    }

    @Test
    fun `rules_jvm_external dependencies may be pinned`() {
        givenBuildScript(
            """
            plugins {
                base
                id("com.github.zetten.bazel-dependencies-plugin")
            }

            repositories {
                jcenter()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                createMavenInstallJson.set(true)
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder.root.resolve("build/java_repositories.bzl")
        assertThat(outputFile.exists(), equalTo(true))
        assertThat(
            outputFile.readText(),
            equalTo(
                BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external.bzl")
                    .readText()
            )
        )
        val mavenInstall = temporaryFolder.root.resolve("build/maven_install.json")
        assertThat(mavenInstall.exists(), equalTo(true))
        assertThat(
            mavenInstall.readText(),
            equalTo(BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install.json").readText())
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
                jcenter()
            }

            val generate by configurations.creating

            dependencies {
                generate("com.google.guava:guava:26.0-jre")
                generate("dom4j:dom4j:1.6.1")
            }

            bazelDependencies {
                configuration.set(generate)
                outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
                createMavenInstallJson.set(true)
            }
        """.trimIndent()
        )

        build("generateRulesJvmExternal")

        val outputFile = temporaryFolder.root.resolve("build/java_repositories.bzl")
        assertThat(outputFile.exists(), equalTo(true))
        assertThat(
            outputFile.readText(),
            equalTo(
                BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_rules_jvm_external.bzl")
                    .readText()
            )
        )
        val mavenInstall = temporaryFolder.root.resolve("build/maven_install.json")
        assertThat(mavenInstall.exists(), equalTo(true))
        assertThat(
            mavenInstall.readText(),
            equalTo(BazelDependenciesPluginTest::class.java.getResource("/expected_maven_install.json").readText())
        )

        mavenInstall.resolveSibling(".maven_install.json.tmp").let { tmpMavenInstall ->
            tmpMavenInstall.bufferedWriter().use { writer ->
                var first = true
                mavenInstall.forEachLine { line ->
                    if (first) {
                        first = false
                    } else {
                        writer.newLine()
                    }
                    writer.write(line.replace(Regex("https(:/)?/jcenter.bintray.com/")) { "http${it.groupValues[1]}/customrepo.example.com/context/" })
                }
            }
            tmpMavenInstall.copyTo(mavenInstall, true)
            tmpMavenInstall.delete()
        }

        build("rehashMavenInstall")
        assertThat(mavenInstall.exists(), equalTo(true))
        assertThat(
            mavenInstall.readText(),
            equalTo(BazelDependenciesPluginTest::class.java.getResource("/rehashed_maven_install.json").readText())
        )
    }

    private fun build(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .forwardStdOutput(System.out.bufferedWriter())
            .forwardStdError(System.err.bufferedWriter())
            .withProjectDir(temporaryFolder.root)
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
        temporaryFolder.newFile(fileName)

}