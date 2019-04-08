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
        givenBuildScript("""
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
                configuration = generate
                outputFile = project.buildDir.resolve("java_repositories.bzl")
                strictLicenses = true
            }
        """.trimIndent())

        ex.expectMessage("Could not determine a license for dom4j:dom4j:1.6.1")
        build("generateWorkspace").output.trimEnd()
    }

    @Test
    fun `per-dependency licenses can be set and overridden and sources checked`() {
        givenBuildScript("""
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
                configuration = generate
                outputFile = project.buildDir.resolve("java_repositories.bzl")
                licenseOverrides = mapOf(
                    Pair("dom4j:dom4j:1.6.1", "notice"), // https://github.com/dom4j/dom4j/blob/master/LICENSE
                    Pair("com.google.code.findbugs:jsr305:3.0.2", "restricted") // overrides "notice"
                )
                safeSources = true
                sourcesChecksums = true
            }
        """.trimIndent())

        build("generateWorkspace")

        val outputFile = temporaryFolder.root.resolve("build/java_repositories.bzl")
        assertThat(outputFile.exists(), equalTo(true))
        assertThat(outputFile.readText(), equalTo(BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_license_overrides.bzl").readText()))
    }

    @Test
    fun `non-strict license mode can be used and dependencies attribute changed`() {
        givenBuildScript("""
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
                configuration = generate
                outputFile = project.buildDir.resolve("java_repositories.bzl")
                strictLicenses = false
                dependenciesAttr = "runtime_deps"
            }
        """.trimIndent())

        build("generateWorkspace")

        val outputFile = temporaryFolder.root.resolve("build/java_repositories.bzl")
        assertThat(outputFile.exists(), equalTo(true))
        assertThat(outputFile.readText(), equalTo(BazelDependenciesPluginTest::class.java.getResource("/expected_java_repositories_non_strict.bzl").readText()))
    }

    private fun build(vararg arguments: String): BuildResult =
            GradleRunner
                    .create()
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