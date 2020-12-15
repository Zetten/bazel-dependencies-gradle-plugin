package com.github.zetten.bazeldeps

import com.github.jk1.license.reader.CachedModuleReader
import com.github.jk1.license.reader.ConfigurationReader
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.jvm.JvmLibrary
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getArtifacts
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
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

        val bazelDependencies = extensions.findByName("bazelDependencies") as BazelDependencies

        val projectRepositories = project.provider {
            project.repositories.withType(MavenArtifactRepository::class.java).map { r -> r.url.toString() }
        }
        val projectDependencies = projectDependencies(
            project,
            bazelDependencies.configuration,
            bazelDependencies.sourcesChecksums,
            bazelDependencies.compileOnly,
            bazelDependencies.testOnly
        )
        tasks.create("generateJvmMavenImportExternal", GenerateJvmMavenImportExternal::class) {
            outputFile.set(bazelDependencies.outputFile)
            dependencies.set(projectDependencies)
            repositories.set(projectRepositories)
            licenseData.set(
                dependencyLicenseData(
                    project,
                    bazelDependencies.configuration,
                    projectDependencies,
                    bazelDependencies.licenseOverrides
                )
            )
            strictLicenses.set(bazelDependencies.strictLicenses)
            dependenciesAttr.set(bazelDependencies.dependenciesAttr)
            safeSources.set(bazelDependencies.safeSources)
        }

        tasks.create("generateRulesJvmExternal", GenerateRulesJvmExternal::class) {
            outputFile.set(bazelDependencies.outputFile)
            createMavenInstallJson.set(bazelDependencies.createMavenInstallJson)
            mavenInstallJsonFile.set(bazelDependencies.outputFile.map { it.resolveSibling("maven_install.json") })
            dependencies.set(projectDependencies)
            repositories.set(projectRepositories)
        }

        tasks.create("rehashMavenInstall", RehashMavenInstall::class) {
            mavenInstallJsonFile.set(bazelDependencies.outputFile.map { it.resolveSibling("maven_install.json") })
        }
    }
}

open class BazelDependencies(objects: ObjectFactory) {
    val configuration: Property<Configuration> = objects.property()
    val outputFile: Property<File> = objects.property()
    var strictLicenses: Property<Boolean> = objects.property<Boolean>().convention(true)
    var licenseOverrides: MapProperty<String, String> = objects.mapProperty()
    var compileOnly: SetProperty<String> = objects.setProperty()
    var testOnly: SetProperty<String> = objects.setProperty()
    var dependenciesAttr: Property<String> = objects.property<String>().convention("exports")
    var safeSources: Property<Boolean> = objects.property<Boolean>().convention(false)
    var sourcesChecksums: Property<Boolean> = objects.property<Boolean>().convention(false)
    var createMavenInstallJson: Property<Boolean> = objects.property<Boolean>().convention(true)
}

fun projectDependencies(
    project: Project,
    configuration: Property<Configuration>,
    sourcesChecksums: Property<Boolean>,
    compileOnly: SetProperty<String>,
    testOnly: SetProperty<String>
): Provider<Set<ProjectDependency>> = project.provider {
    configuration.get().resolvedConfiguration.firstLevelModuleDependencies
        .filter { it.moduleArtifacts.isNotEmpty() }
        .flatMap { walkDependencies(it, project, sourcesChecksums.get(), compileOnly.get(), testOnly.get()) }
        .toHashSet()
}

private fun walkDependencies(
    resolvedDependency: ResolvedDependency,
    project: Project,
    resolveSrcJars: Boolean,
    compileOnly: Set<String>,
    testOnly: Set<String>
): Iterable<ProjectDependency> {
    val dependenciesWithArtifacts = resolvedDependency.children.filter { it.moduleArtifacts.isNotEmpty() }
    val transitiveDeps =
        dependenciesWithArtifacts.flatMap { walkDependencies(it, project, resolveSrcJars, compileOnly, testOnly) }
            .toSet()
    val firstOrderDeps =
        dependenciesWithArtifacts.map { i -> transitiveDeps.first { j -> i.module.id == j.id } }.toSet()

    val id = resolvedDependency.module.id
    val classifier = resolvedDependency.moduleArtifacts.first().classifier
    val jar = resolvedDependency.moduleArtifacts.first().file

    val dep = ProjectDependency(
        id = id,
        classifier = classifier,
        dependencies = firstOrderDeps,
        allDependencies = transitiveDeps,
        jar = jar,
        srcJar = if (resolveSrcJars) findSrcJar(id, project) else null,
        neverlink = compileOnly.contains(
            if (classifier != null) {
                "$id:$classifier"
            } else {
                id.toString()
            }
        ),
        testonly = testOnly.contains(
            if (classifier != null) {
                "$id:$classifier"
            } else {
                id.toString()
            }
        )
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
        project.logger.warn("Artifact had multiple sources artifacts! Returning no srcJar for ${id}")
    }
    return null
}

fun dependencyLicenseData(
    project: Project,
    configuration: Provider<Configuration>,
    projectDependencies: Provider<Set<ProjectDependency>>,
    licenseOverrides: Provider<Map<String, String>>
): Provider<Map<ProjectDependency, List<LicenseData>>> = project.provider {
    val result: MutableMap<ProjectDependency, List<LicenseData>> = mutableMapOf()
    val licenseConfigData = ConfigurationReader(CachedModuleReader()).read(project, configuration.get())
    for (it in projectDependencies.get()) {
        val ld = ArrayList<LicenseData>()
        val licenseOverride = licenseOverrides.get()[it.getMavenIdentifier()]
        if (licenseOverride != null) {
            project.logger.debug(
                "Overriding license for {} with {}",
                it.getMavenIdentifier(),
                licenseOverride
            )
            ld.add(LicenseData(null, null, licenseOverride))
        } else {
            project.logger.debug("Using real licenses for {}", it.getMavenIdentifier())
            val licenses = licenseConfigData.dependencies
                .filter { d -> it.id.group == d.group && it.id.name == d.name && it.id.version == d.version }
                .flatMap { md -> md.poms }
                .flatMap { pom -> pom.licenses }
            for (l in licenses) {
                ld.add(LicenseData(l.name, l.url, null))
            }
        }
        result[it] = ld
    }
    result
}