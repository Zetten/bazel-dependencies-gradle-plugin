package com.github.zetten.bazeldeps

import com.google.common.graph.Graphs
import com.google.common.graph.MutableNetwork
import com.google.common.graph.NetworkBuilder
import net.swiftzer.semver.SemVer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.gradle.jvm.JvmLibrary
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getArtifacts
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withArtifacts
import org.gradle.language.base.artifact.SourcesArtifact
import java.io.File

fun reconcileAttribute(container: AttributeContainer, attribute: Attribute<*>): Pair<Attribute<*>, *> =
    Pair(attribute, container.getAttribute(attribute)!!)

fun <T> AttributeContainer.foo(key: Attribute<T>): Pair<Attribute<T>, T?> = Pair(key, this.getAttribute(key))


class BazelDependenciesPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = target.run {
        extensions.create<BazelDependenciesExtension>("bazelDependencies")

        val bazelDependenciesExtension = extensions.findByType<BazelDependenciesExtension>()!!

        val projectRepositories = project.provider {
            project.repositories.withType(MavenArtifactRepository::class.java).map { r -> r.url.toString() }
        }

        val projectDependencies: Provider<Set<ProjectDependency>> =
            bazelDependenciesExtension.configuration.map { configuration ->
                // Resolve the different dependency scopes (api/compile/runtime) by creating new configurations
                // with the same dependencies of the target configuration, but with different usage attributes.
                val apiConfiguration = configuration.copy()
                    .attributes { attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_API)) }
                val runtimeConfiguration = configuration.copy()
                    .attributes { attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME)) }

                val apiDeps = apiConfiguration.resolvedConfiguration.firstLevelModuleDependencies
                    .filter { it.moduleArtifacts.isNotEmpty() }
                val runtimeDeps = runtimeConfiguration.resolvedConfiguration.firstLevelModuleDependencies
                    .filter { it.moduleArtifacts.isNotEmpty() }

                project.configurations.remove(apiConfiguration)
                project.configurations.remove(runtimeConfiguration)

                val dependencyGraph: MutableNetwork<DependencyGraphNode, DependencyGraphEdge> =
                    NetworkBuilder.directed()
                        .allowsSelfLoops(false)
                        .allowsParallelEdges(true)
                        .build();

                val projectContext = ProjectContext(
                    project,
                    configuration,
                    neverlinkDeps = bazelDependenciesExtension.neverlink.get(),
                    testonlyDeps = bazelDependenciesExtension.testonly.get()
                )

                fun loadDep(artifact: ResolvedDependency, usage: DependencyUsage) {
                    val thisNode = DependencyGraphNode(artifact, projectContext)
                    artifact.children
                        .filter { it.moduleArtifacts.isNotEmpty() && thisNode.exclusions.none { excludeRule -> excludeRule.group == it.moduleGroup && excludeRule.artifact == it.moduleName } }
                        .forEach { dep ->
                            val depNode = DependencyGraphNode(dep, projectContext)
                            val dependencyGraphEdge = DependencyGraphEdge(
                                ArtifactIdentifier.from(artifact),
                                ArtifactIdentifier.from(dep),
                                usage
                            )
                            if (dependencyGraph.addEdge(thisNode, depNode, dependencyGraphEdge)) {
                                loadDep(dep, usage)
                            }
                        }
                }

                apiDeps.forEach { loadDep(it, DependencyUsage.API) }
                runtimeDeps.forEach { loadDep(it, DependencyUsage.RUNTIME) }

                if (Graphs.hasCycle(dependencyGraph)) {
                    // TODO Find and print the cycle
                    throw IllegalStateException("Dependency cycle detected; Bazel will not be able to load. Try adding an exclusion.")
                }

                dependencyGraph.nodes().map {
                    val thisApiDeps =
                        dependencyGraph.outEdges(it).filter { it.usage == DependencyUsage.API }.map { it.target }
                    val thisRuntimeDeps =
                        dependencyGraph.outEdges(it).filter { it.usage == DependencyUsage.RUNTIME }.map { it.target }

                    val regularDeps = thisApiDeps intersect thisRuntimeDeps

                    val id = ArtifactIdentifier(it.id, it.packaging, it.classifier)
                    ProjectDependency(
                        id = id,
                        dependencies = regularDeps,
                        runtimeOnlyDependencies = thisRuntimeDeps subtract regularDeps,
                        compileOnlyDependencies = thisApiDeps subtract regularDeps,
                        jar = it.jar,
                        srcJar = it.srcJar,
                        exclusions = it.exclusions,
                        neverlink = it.neverlink,
                        testonly = it.testonly,
                    )
                }.toSet()
            }

        tasks.register<GenerateJvmImportExternal>("generateJvmImportExternal") {
            group = "build"
            dependencies.set(projectDependencies)
            repositories.set(projectRepositories)
            outputFile.set(bazelDependenciesExtension.outputFile)
            createAggregatorRepo.set(bazelDependenciesExtension.jvmImportExternal.createAggregatorRepo)
        }

        val rulesJvmExternalVersionProvider =
            bazelDependenciesExtension.rulesJvmExternal.version.map { SemVer.parse(it) }

        tasks.register<GenerateRulesJvmExternal>("generateRulesJvmExternal") {
            group = "build"
            dependencies.set(projectDependencies)
            repositories.set(projectRepositories)
            projectExclusions.set(bazelDependenciesExtension.configuration.map { configuration ->
                configuration.excludeRules.map { ProjectDependencyExclusion(it.group, it.module) }.toSet()
            })
            rulesJvmExternalVersion.set(rulesJvmExternalVersionProvider)
            outputFile.set(bazelDependenciesExtension.outputFile)
            mavenInstallJsonFile.set(
                bazelDependenciesExtension.rulesJvmExternal.createMavenInstallJson.flatMap { createMavenInstallJson ->
                    if (createMavenInstallJson) {
                        project.layout.file(bazelDependenciesExtension.outputFile.map {
                            it.asFile.resolveSibling("maven_install.json")
                        })
                    } else {
                        project.objects.fileProperty()
                    }
                })
        }

        tasks.register<RehashMavenInstall>("rehashMavenInstall") {
            group = "build"
            javaRepositoriesBzlFile.set(bazelDependenciesExtension.outputFile)
            mavenInstallJsonFile.set(
                bazelDependenciesExtension.rulesJvmExternal.createMavenInstallJson.flatMap { createMavenInstallJson ->
                    if (!createMavenInstallJson) {
                        throw IllegalStateException("Rehashing maven_install.json requires createMavenInstallJson=true")
                    }
                    project.layout.file(bazelDependenciesExtension.outputFile.map {
                        it.asFile.resolveSibling("maven_install.json")
                    })
                })
            rulesJvmExternalVersion.set(rulesJvmExternalVersionProvider)
        }
    }
}

private data class ProjectContext(
    val project: Project,
    val configuration: Configuration,
    val neverlinkDeps: MutableSet<String>,
    val testonlyDeps: MutableSet<String>,
)

private enum class DependencyUsage {
    API, RUNTIME
}

private data class DependencyGraphNode(
    val id: ModuleVersionIdentifier,
    val classifier: String?,
    val jar: File,
    val srcJar: File? = null,
    val packaging: String = jar.extension,
    val exclusions: Set<ProjectDependencyExclusion> = emptySet(),
    val neverlink: Boolean = false,
    val testonly: Boolean = false,
) {
    constructor(artifact: ResolvedDependency, projectContext: ProjectContext) :
            this(
                id = artifact.module.id,
                classifier = artifact.moduleArtifacts.first().classifier,
                jar = artifact.moduleArtifacts.first().file,
                srcJar = findSrcJar(projectContext.project, artifact.module.id),
                exclusions = projectContext.configuration.dependencies
                    .filter { artifact.module.id.toString().startsWith("${it.group}:${it.name}:${it.version ?: ""}") }
                    .flatMap { (it as ExternalModuleDependency).excludeRules }
                    .map { ProjectDependencyExclusion(it.group, it.module) }
                    .toSet(),
                neverlink = projectContext.neverlinkDeps.contains("${artifact.module.id.group}:${artifact.module.id.name}"),
                testonly = projectContext.testonlyDeps.contains("${artifact.module.id.group}:${artifact.module.id.name}"),
            )
}

private data class DependencyGraphEdge(
    val source: ArtifactIdentifier,
    val target: ArtifactIdentifier,
    val usage: DependencyUsage
)

private fun findSrcJar(project: Project, id: ModuleVersionIdentifier): File? {
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
        project.logger.warn("Artifact had multiple sources artifacts! Returning no srcJar for $id")
    }
    return null
}
