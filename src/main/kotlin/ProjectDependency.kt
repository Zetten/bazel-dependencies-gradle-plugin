package com.github.zetten.bazeldeps

import org.gradle.api.artifacts.ModuleVersionIdentifier
import java.io.File
import java.io.Serializable

data class ProjectDependency(
        val id: ModuleVersionIdentifier,
        val classifier: String?,
        val dependencies: Set<ProjectDependency>,
        val jar: File,
        val srcJar: File?
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
            "$id:${classifier}"
        } else {
            id.toString()
        }
    }

    private fun sanitize(s: String): String {
        return s.replace(Regex("[^\\w]"), "_")
    }

    override fun compareTo(other: ProjectDependency): Int {
        return getBazelIdentifier().compareTo(other.getBazelIdentifier())
    }

}