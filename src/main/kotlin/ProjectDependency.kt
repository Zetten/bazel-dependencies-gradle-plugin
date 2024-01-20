package  com.github.zetten.bazeldeps

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import java.io.BufferedInputStream
import java.io.File
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.util.Arrays
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ArtifactIdentifier(
    val group: String,
    val name: String,
    val version: String,
    val packaging: String,
    val classifier: String? = null,
) : Comparable<ArtifactIdentifier>, Serializable {
    constructor(id: ModuleVersionIdentifier, packaging: String, classifier: String? = null) :
            this(id.group, id.name, id.version, packaging, classifier)

    companion object {
        fun from(artifact: ResolvedDependency) = ArtifactIdentifier(
            artifact.module.id,
            artifact.moduleArtifacts.first().file.extension,
            artifact.moduleArtifacts.first().classifier
        )
    }

    fun getBazelIdentifier(): String = when {
        classifier != null -> sanitizeTargetName("${group}_${name}_${classifier.trim()}")
        else -> sanitizeTargetName("${group}_${name}")
    }

    fun getMavenIdentifier(): String = when {
        classifier != null -> "$group:$name:$version:$classifier"
        else -> "$group:$name:$version"
    }

    // TODO either type+classifier or neither - no type-only mapping possible?
    // https://github.com/google/bazel-common/blob/f1115e0f777f08c3cdb115526c4e663005bec69b/tools/maven/pom_file.bzl#L144
    fun getRulesJvmExternalCoordinates(): String = when {
        // If classifier is present, packaging must be provided...
        classifier != null -> "${group}:${name}:${packaging}:${classifier}:${version}"
        // ...otherwise it's optional, with default=jar
        else -> "${group}:${name}${getArtifactPackaging()}:${version}"
    }

    fun getArtifactPackaging(): String = when {
        packaging != "jar" -> ":$packaging"
        else -> ""
    }

    override fun toString(): String = getRulesJvmExternalCoordinates()

    override fun compareTo(other: ArtifactIdentifier): Int =
        getBazelIdentifier().compareTo(other.getMavenIdentifier())
}

data class ProjectDependencyExclusion(val group: String, val artifact: String) :
    Comparable<ProjectDependencyExclusion>, Serializable {
    companion object {
        val comparator = compareBy(ProjectDependencyExclusion::group).thenBy(ProjectDependencyExclusion::artifact)
    }

    override fun compareTo(other: ProjectDependencyExclusion): Int = comparator.compare(this, other)
}

data class ProjectDependency(
    val id: ArtifactIdentifier,
    val dependencies: Set<ArtifactIdentifier>,
    val runtimeOnlyDependencies: Set<ArtifactIdentifier>,
    val compileOnlyDependencies: Set<ArtifactIdentifier>,
    val jar: File? = null,
    val srcJar: File? = null,
    val exclusions: Set<ProjectDependencyExclusion> = emptySet(),
    val neverlink: Boolean = false,
    val testonly: Boolean = false,
) : Comparable<ProjectDependency>, Serializable {

    fun getBazelIdentifier(): String = id.getBazelIdentifier()

    fun getMavenIdentifier(): String = id.getMavenIdentifier()

    fun findArtifactRepositories(repositories: List<String> = emptyList()): List<String> = repositories.filter {
        artifactExists("${getArtifactUrl(getMavenIdentifier(), it)}.${jar!!.extension}")
    }

    fun findArtifactUrls(repositories: List<String> = emptyList()): List<String> = repositories.mapNotNull {
        val artifactUrl = "${getArtifactUrl(getMavenIdentifier(), it)}.${id.packaging}"
        if (artifactExists(artifactUrl)) artifactUrl else null
    }

    fun findSrcjarUrls(repositories: List<String> = emptyList()): List<String> = repositories.mapNotNull {
        val artifactUrl = "${getArtifactUrl(getMavenIdentifier(), it)}-sources.jar"
        if (artifactExists(artifactUrl)) artifactUrl else null
    }

    internal fun getArtifactUrl(repoUrl: String) = getArtifactUrl(getMavenIdentifier(), repoUrl)

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
            group.replace('.', '/'), artifact, version, "${artifact}-${file_version}"
        ).joinToString("/")
    }

    private fun artifactExists(artifactUrl: String): Boolean {
        val code = with(URL(artifactUrl).openConnection() as HttpURLConnection) {
            requestMethod = "HEAD"
            connect()
            responseCode
        }
        return code in (100..399)
    }

    internal fun computeTransitiveDependencyClosure(keyedDependencies: Map<ArtifactIdentifier, ProjectDependency>): List<ArtifactIdentifier> {
        return (
                dependencies + dependencies.flatMap {
                    keyedDependencies[it]!!.computeTransitiveDependencyClosure(keyedDependencies)
                }
                ).toSet().sorted()
    }

    internal fun computeDependencyPackages(): Set<String> {
        if (this.jar == null) return emptySet()

        val packages = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(jar.inputStream())).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val entryName = entry!!.name
                if (!entryName.endsWith(".class")) {
                    continue
                }
                if ("module-info.class" == entryName || entryName.endsWith("/module-info.class")) {
                    continue
                }
                packages.add(extractPackageName(entryName))
            }
        }
        return packages
    }

    private fun extractPackageName(zipEntryName: String): String {
        val parts = zipEntryName.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size == 1) {
            return ""
        }
        var skip = 0L
        // As per https://docs.oracle.com/en/java/javase/13/docs/specs/jar/jar.html
        if (parts.size > 3 && "META-INF" == parts[0] && "versions" == parts[1] && "[1-9][0-9]*".toRegex()
                .matches(parts[2])
        ) {
            skip = 3
        }

        // -1 for the class name, -skip for the skipped META-INF prefix.
        val limit = parts.size - 1 - skip
        return Arrays.stream(parts).skip(skip).limit(limit).collect(Collectors.joining("."))
    }

    override fun compareTo(other: ProjectDependency): Int {
        return getBazelIdentifier().compareTo(other.getBazelIdentifier())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProjectDependency
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

internal fun sanitizeTargetName(s: String): String = s.replace(Regex("\\W"), "_")