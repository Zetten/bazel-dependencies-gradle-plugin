package com.github.zetten.bazeldeps

import com.github.jk1.license.reader.ConfigurationReader
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.jvm.JvmLibrary
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getArtifacts
import org.gradle.kotlin.dsl.withArtifacts
import org.gradle.language.base.artifact.SourcesArtifact
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class BazelDependenciesPlugin : Plugin<Project> {
    private val logger: Logger = LoggerFactory.getLogger(BazelDependenciesPlugin::class.java)

    override fun apply(project: Project): Unit = project.run {
        extensions.create("bazelDependencies", BazelDependencies::class)
        plugins.apply("com.github.jk1.dependency-license-report")

        afterEvaluate {
            val bazelDependencies = extensions.findByName("bazelDependencies") as BazelDependencies

            val configuration = bazelDependencies.configuration
            val projectDependencies = configuration.resolvedConfiguration.firstLevelModuleDependencies
                    .flatMap { walkDependencies(it, project) }
                    .toHashSet()

            val licenseConfigData = ConfigurationReader().read(project, configuration)
            val dependencyLicenseData = HashMap<ProjectDependency, List<LicenseData>>()

            for (it in projectDependencies) {
                val ld = ArrayList<LicenseData>()
                if (bazelDependencies.licenseOverrides[it.getMavenIdentifier()] != null) {
                    logger.debug("Overriding license for {} with {}", it.getMavenIdentifier(), bazelDependencies.licenseOverrides[it.getMavenIdentifier()])
                    ld.add(LicenseData(null, null, bazelDependencies.licenseOverrides[it.getMavenIdentifier()]))
                } else {
                    logger.debug("Using real licenses for {}", it.getMavenIdentifier())
                    val licenses = licenseConfigData.dependencies
                            .filter { d -> it.id.group == d.group && it.id.name == d.name && it.id.version == d.version }
                            .flatMap { md -> md.poms }
                            .flatMap { pom -> pom.licenses }
                    for (l in licenses) {
                        ld.add(LicenseData(l.name, l.url, null))
                    }
                }
                dependencyLicenseData[it] = ld
            }

            bazelDependencies.outputFile.parentFile.mkdirs()

            tasks.create("generateWorkspace", GenerateWorkspace::class) {
                outputFile = bazelDependencies.outputFile
                dependencies = projectDependencies
                repositories = project.repositories.withType(MavenArtifactRepository::class.java).map { r -> r.url.toString() }
                licenseData = dependencyLicenseData
                strictLicenses = bazelDependencies.strictLicenses
            }
        }
    }

    private fun walkDependencies(resolvedDependency: ResolvedDependency, project: Project): Iterable<ProjectDependency> {
        val transitiveDeps = resolvedDependency.children.flatMap { walkDependencies(it, project) }.toSet()
        val firstOrderDeps = resolvedDependency.children.map { i -> transitiveDeps.first { j -> i.module.id == j.id } }.toSet()

        val dep = ProjectDependency(
                id = resolvedDependency.module.id,
                classifier = resolvedDependency.moduleArtifacts.first().classifier,
                dependencies = firstOrderDeps,
                jar = resolvedDependency.moduleArtifacts.first().file,
                srcJar = findSrcJar(resolvedDependency.module.id, project)
        )

        return setOf(dep) + transitiveDeps
    }

    private fun findSrcJar(id: ModuleVersionIdentifier, project: Project): File? {
        val sourcesArtifacts = project.dependencies.createArtifactResolutionQuery()
                .forModule(id.group, id.name, id.version)
                .withArtifacts(JvmLibrary::class, SourcesArtifact::class)
                .execute()
                .resolvedComponents
                .flatMap { it.getArtifacts(SourcesArtifact::class) }
                .toSet()

        if (sourcesArtifacts.size == 1) {
            return (sourcesArtifacts.first() as ResolvedArtifactResult).file
        } else if (sourcesArtifacts.size > 1) {
            logger.warn("Artifact had multiple sources artifacts! Returning no srcJar for ${id}")
        }
        return null
    }

}

open class BazelDependencies {
    lateinit var configuration: Configuration
    lateinit var outputFile: File
    var strictLicenses: Boolean = true
    var licenseOverrides: Map<String, String> = mapOf()
}
