package com.github.zetten.bazeldeps

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.reader.CachedModuleReader
import com.github.jk1.license.reader.ConfigurationReader
import net.swiftzer.semver.SemVer
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
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getArtifacts
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.kotlin.dsl.withArtifacts
import org.gradle.language.base.artifact.SourcesArtifact
import java.io.File

class BazelDependenciesPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        extensions.create("bazelDependencies", BazelDependencies::class)

        val bazelDependencies = extensions.findByName("bazelDependencies") as BazelDependencies

        if (!plugins.hasPlugin("com.github.jk1.dependency-license-report")) {
            plugins.apply("com.github.jk1.dependency-license-report")
            (extensions["licenseReport"] as LicenseReportExtension).run {
                filters = arrayOf(LicenseBundleNormalizer())
            }
        }

        val projectRepositories = project.provider {
            project.repositories.withType(MavenArtifactRepository::class.java).map { r -> r.url.toString() }
        }
        val compileOnlyMatchers =
            bazelDependencies.compileOnly.map { it.map { compileOnly -> ProjectDependencyMatcher.of(compileOnly) } }
        val testOnlyMatchers =
            bazelDependencies.testOnly.map { it.map { testOnly -> ProjectDependencyMatcher.of(testOnly) } }
        val projectDependencies = projectDependencies(
            project,
            bazelDependencies.configuration,
            bazelDependencies.sourcesChecksums,
            compileOnlyMatchers,
            testOnlyMatchers,
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
            dependencies.set(projectDependencies)
            repositories.set(projectRepositories)
            createMavenInstallJson.set(bazelDependencies.createMavenInstallJson)
            rulesJvmExternalVersion.set(bazelDependencies.rulesJvmExternalVersion.map { SemVer.parse(it) })
            outputFile.set(bazelDependencies.outputFile)
            mavenInstallJsonFile.set(bazelDependencies.outputFile.map { it.resolveSibling("maven_install.json") })
        }

        tasks.create("rehashMavenInstall", RehashMavenInstall::class) {
            javaRepositoriesBzlFile.set(bazelDependencies.outputFile)
            mavenInstallJsonFile.set(bazelDependencies.outputFile.map { it.resolveSibling("maven_install.json") })
            rulesJvmExternalVersion.set(bazelDependencies.rulesJvmExternalVersion.map { SemVer.parse(it) })
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
    var rulesJvmExternalVersion: Property<String> = objects.property<String>().convention("4.0")
}

fun projectDependencies(
    project: Project,
    configuration: Property<Configuration>,
    sourcesChecksums: Property<Boolean>,
    compileOnly: Provider<List<ProjectDependencyMatcher>>,
    testOnly: Provider<List<ProjectDependencyMatcher>>
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
    compileOnly: List<ProjectDependencyMatcher>,
    testOnly: List<ProjectDependencyMatcher>
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
        neverlink = compileOnly.any { it.matches(id, classifier) },
        testonly = testOnly.any { it.matches(id, classifier) }
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
    val licenseReportExtension = project.extensions["licenseReport"] as LicenseReportExtension
    val licenseConfigData =
        ConfigurationReader(licenseReportExtension, CachedModuleReader(licenseReportExtension)).read(
            project,
            configuration.get()
        )
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

data class ProjectDependencyMatcher(
    val group: String,
    val name: String,
    val version: String? = null,
    val classifier: String? = null
) {
    companion object {
        fun of(id: String): ProjectDependencyMatcher {
            return id.split(':').let {
                when (it.size) {
                    2 -> ProjectDependencyMatcher(it[0], it[1])
                    3 -> ProjectDependencyMatcher(it[0], it[1], it[2])
                    4 -> ProjectDependencyMatcher(it[0], it[1], it[2], it[3])
                    else -> throw IllegalArgumentException("Could not parse module identifier from $id")
                }
            }
        }
    }

    fun matches(id: ModuleVersionIdentifier, classifier: String?): Boolean =
        this.group == id.group
                && this.name == id.name
                && (this.version == null || this.version == id.version)
                && (this.classifier == null || this.classifier == classifier)
}
