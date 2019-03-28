package com.github.zetten.bazeldeps

import org.gradle.internal.hash.HashUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

class GenerateDependencySnippet @Inject constructor(
        private val outputFile: File,
        private val dependency: ProjectDependency,
        private val repositories: List<String>,
        private val licenseData: List<LicenseData>,
        private val strictLicenses: Boolean) : Runnable {
    private val logger: Logger = LoggerFactory.getLogger(GenerateDependencySnippet::class.java)

    override fun run() {
        logger.info("Generating Bazel repository rule for ${dependency.id}")

        val mostRestrictiveLicense = try {
            Licenses.getMostRestrictiveLicense(licenseData)
        } catch (e: Exception) {
            if (strictLicenses) throw IllegalStateException("Could not determine a license for ${dependency.getMavenIdentifier()}", e)
            "none"
        }

        val jarSha256 = HashUtil.sha256(dependency.jar).asZeroPaddedHexString(64)
        val jarUrls = findArtifactRepositories(dependency.getMavenIdentifier(), dependency.jar!!.extension)

        outputFile.writeText("""
                |def ${dependency.getBazelIdentifier()}(fetch_sources, replacements):
                |    jvm_maven_import_external(
                |        name = "${dependency.getBazelIdentifier()}",
                |        artifact = "${dependency.getJvmMavenImportExternalCoordinates()}",
                |        ${jarUrls.sorted().joinToString("\n", prefix = "server_urls = [\n", postfix = "\n        ") { "            \"${it}\"," }}],
                |        artifact_sha256 = "${jarSha256}",
                |        licenses = ["${mostRestrictiveLicense}"],
                |        fetch_sources = fetch_sources,
                |        ${dependency.dependencies.map { it.getBazelIdentifier() }.joinToString("", prefix = "runtime_deps = _replace_dependencies([", postfix = "\n        ") { "\n            \"@${it}\"," }}], replacements),
                |    )
                |""".trimMargin()
        )
    }

    private fun findArtifactRepositories(mavenIdentifier: String, extension: String): List<String> {
        return repositories.mapNotNull {
            val artifactUrl = "${getArtifactUrl(mavenIdentifier, it)}.${extension}"
            val code = with(URL(artifactUrl).openConnection() as HttpURLConnection) {
                requestMethod = "HEAD"
                connect()
                responseCode
            }
            logger.debug("Received ${code} for artifact at ${artifactUrl}")
            if (code in 100..399) it else null
        }
    }

    private fun getArtifactUrl(mavenIdentifier: String, repoUrl: String): String {
        val parts = mavenIdentifier.split(':')

        val (group, artifact, version, file_version) = if (parts.size == 4) {
            val (group, artifact, version, classifier) = parts
            val fileVersion = "${version}-${classifier}"
            arrayOf(group, artifact, version, fileVersion)
        } else {
            val (group, artifact, version) = parts
            arrayOf(group, artifact, version, version)
        }

        return (if (repoUrl.endsWith('/')) repoUrl else "$repoUrl/") + arrayOf(
                group.replace('.', '/'),
                artifact,
                version,
                "${artifact}-${file_version}"
        ).joinToString("/")
    }

}
