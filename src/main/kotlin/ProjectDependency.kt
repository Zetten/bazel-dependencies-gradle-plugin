package com.github.zetten.bazeldeps

import org.gradle.api.artifacts.ModuleVersionIdentifier
import java.io.File
import java.io.Serializable

data class ProjectDependency(
        val id: ModuleVersionIdentifier,
        val classifier: String?,
        val dependencies: Set<ProjectDependency>,
        val jar: File? = null
) : Comparable<ProjectDependency>, Serializable {

    fun getBazelIdentifier(): String {
        return if (classifier != null) {
            sanitize(id.group) + "_" + sanitize(id.name) + "_" + sanitize(classifier.trim())
        } else {
            sanitize(id.group) + "_" + sanitize(id.name)
        }
    }

    fun getMavenIdentifier(): String {
        return if (classifier != null) {
            "$id:$classifier"
        } else {
            id.toString()
        }
    }

    fun getJvmMavenImportExternalCoordinates(): String {
        return if (classifier != null) {
            // If classifier is present, packaging must be provided...
            "${id.group}:${id.name}:${jar!!.extension}:${classifier}:${id.version}"
        } else {
            // ...otherwise it's optional, with default=jar
            "${id.group}:${id.name}${getArtifactPackaging()}:${id.version}"
        }
    }

    fun getMavenCoordinatesTag(): String {
        return if (classifier != null) {
            // TODO either type+classifier or neither - no type-only mapping possible?
            // https://github.com/google/bazel-common/blob/f1115e0f777f08c3cdb115526c4e663005bec69b/tools/maven/pom_file.bzl#L144
            "$id:${jar!!.extension}:$classifier"
        } else {
            id.toString()
        }
    }

    private fun getArtifactPackaging(): String {
        return if (!jar!!.extension.equals("jar")) {
            ":${jar.extension}"
        } else {
            ""
        }
    }

    private fun sanitize(s: String): String {
        return s.replace(Regex("[^\\w]"), "_")
    }

    override fun compareTo(other: ProjectDependency): Int {
        return getBazelIdentifier().compareTo(other.getBazelIdentifier())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProjectDependency

        if (id != other.id) return false
        if (classifier != other.classifier) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (classifier?.hashCode() ?: 0)
        return result
    }

}