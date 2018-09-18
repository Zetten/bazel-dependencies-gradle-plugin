package com.github.zetten.bazeldeps

import org.gradle.api.artifacts.ModuleVersionIdentifier
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
        val jarUrls = findArtifactUrls(dependency.getMavenIdentifier())
        val (srcJarSha256, srcJarUrls) = findSrcJar(dependency.id, dependency.srcJar)

        val jarSnippet = """
            jar_urls = [
                ${jarUrls.sorted().joinToString("\n                ") { "\"${it}\"," }}
            ],
            jar_sha256 = "${jarSha256}","""
        val srcJarSnippet = if (srcJarSha256 == null) "" else """
            srcjar_urls = [
                ${srcJarUrls.sorted().joinToString("\n                ") { "\"${it}\"," }}
            ],
            srcjar_sha256 = "${srcJarSha256}","""
        val depsSnippet = if (dependency.dependencies.isEmpty()) "" else """
            runtime_deps = _replace_dependencies([
                ${dependency.dependencies.map { it.getBazelIdentifier() }.sorted().joinToString("\n                ") { "\"@${it}\"," }}
            ], replacements),"""

        outputFile.writeText("""    if "${dependency.getBazelIdentifier()}" not in excludes:
        java_import_external(
            name = "${dependency.getBazelIdentifier()}",
            licenses = ["${mostRestrictiveLicense}"],""")
        if (!jarSnippet.isEmpty()) {
            outputFile.appendText(jarSnippet)
        }
        if (!srcJarSnippet.isEmpty()) {
            outputFile.appendText(srcJarSnippet)
        }
        if (!depsSnippet.isEmpty()) {
            outputFile.appendText(depsSnippet)
        }
        outputFile.appendText("""
        )
""")
    }

    private fun findArtifactUrls(mavenIdentifier: String): List<String> {
        return repositories.mapNotNull {
            val artifactUrl = getArtifactUrl(mavenIdentifier, it) + ".jar"
            val code = with(URL(artifactUrl).openConnection() as HttpURLConnection) {
                requestMethod = "HEAD"
                connect()
                responseCode
            }
            logger.debug("Received ${code} for artifact at ${artifactUrl}")
            if (code in 100..399) artifactUrl else null
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

    private fun findSrcJar(id: ModuleVersionIdentifier, srcJar: File?): SrcJarResult {
        return if (srcJar != null) {
            val srcJarSha256 = HashUtil.sha256(srcJar).asZeroPaddedHexString(64)
            val srcJarUrls = findArtifactUrls(id.toString() + ":sources")
            SrcJarResult(srcJarSha256, srcJarUrls)
        } else
            SrcJarResult(null, listOf())
    }

}

data class SrcJarResult(val srcJarSha256: String?, val srcJarUrls: List<String>)