package com.github.zetten.bazeldeps

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import net.swiftzer.semver.SemVer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.internal.hash.Hashing
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File

class MavenInstallJsonV1(
    workerExecutor: WorkerExecutor,
    rulesJvmExternalVersion: Property<SemVer>,
    temporaryDir: File,
    sortedDependencies: List<ProjectDependency>,
    sortedRepositories: List<String>
) {
    internal val contents: MavenInstallJsonV1Contents

    init {
        val keyedDependencies = sortedDependencies.associateBy(ProjectDependency::id)
        val snippetFiles = sortedDependencies
            .flatMap { dep ->
                val transitiveDependencies = dep.computeTransitiveDependencyClosure(keyedDependencies)
                if (dep.srcJar != null) {
                    listOf(
                        Pair(dep, transitiveDependencies),
                        Pair(
                            dep.copy(
                                id = dep.id.copy(classifier = "sources"),
                                jar = dep.srcJar,
                                dependencies = dep.dependencies
                                    .filter { depDep -> sortedDependencies.any { it.id == depDep && it.srcJar != null } }
                                    .map { depDep -> depDep.copy(classifier = "sources") }.toSet(),
                            ),
                            transitiveDependencies
                                .filter { depDep -> sortedDependencies.any { it.id == depDep && it.srcJar != null } }
                                .map { depDep -> depDep.copy(classifier = "sources") }.toSet()
                        )
                    )
                } else {
                    listOf(Pair(dep, transitiveDependencies))
                }
            }
            .map {
                val dependency = it.first
                val snippetFile = temporaryDir.resolve("${dependency.getBazelIdentifier()}.maven_install.snippet")
                workerExecutor.noIsolation().submit(GenerateMavenInstallV1Snippet::class.java) {
                    getOutputFile().set(snippetFile)
                    getDependency().set(dependency)
                    getTransitiveDependencies().set(it.second)
                    getRepositories().set(sortedRepositories)
                    getRulesJvmExternalVersion().set(rulesJvmExternalVersion.get())
                }
                snippetFile
            }

        workerExecutor.await()

        // read all snippet files back to objects so we can compute the
        // checksums and emit the complete maven_install.json
        val dependencyTreeEntries: List<DependencyTreeEntry> = snippetFiles.map(objectMapper::readValue)
        this.contents =
            MavenInstallJsonV1Contents(
                rulesJvmExternalVersion.get(),
                sortedDependencies,
                dependencyTreeEntries,
                sortedRepositories
            )
    }
}

data class MavenInstallJsonV1Contents(
    @JsonProperty("dependency_tree") val dependencyTree: DependencyTree,
) {
    constructor(
        rulesJvmExternalSemVer: SemVer,
        dependencies: List<ProjectDependency>,
        dependencyTreeEntries: List<DependencyTreeEntry>,
        repositories: List<String>
    ) : this(
        // old rules_jvm_external versions use a different checksum attribute
        if (rulesJvmExternalSemVer < SemVer(4, 1)) {
            DependencyTree(
                dependencies = dependencyTreeEntries,
                oldDependencyTreeSignature = computeDependencyTreeSignature(dependencyTreeEntries)
            )
        } else {
            DependencyTree(
                dependencies = dependencyTreeEntries,
                resolvedArtifactsHash = computeDependencyTreeSignature(dependencyTreeEntries),
                inputArtifactsHash = computeDependencyInputsSignature(
                    rulesJvmExternalSemVer,
                    dependencies,
                    repositories
                )
            )
        }
    )

    companion object Factory {
        fun from(file: File): MavenInstallJsonV1Contents = objectMapper.readValue(file)
    }
}

interface GenerateMavenInstallV1SnippetParams : WorkParameters {
    fun getOutputFile(): RegularFileProperty
    fun getDependency(): Property<ProjectDependency>
    fun getTransitiveDependencies(): ListProperty<ArtifactIdentifier>
    fun getRepositories(): ListProperty<String>
    fun getRulesJvmExternalVersion(): Property<SemVer>
}

abstract class GenerateMavenInstallV1Snippet : WorkAction<GenerateMavenInstallV1SnippetParams> {
    override fun execute() {
        val outputFile = parameters.getOutputFile().asFile.get()
        val dependency = parameters.getDependency().get()
        val transitiveDependencies = parameters.getTransitiveDependencies().get()
        val repositories = parameters.getRepositories().get()
        val rulesJvmExternalVersion = parameters.getRulesJvmExternalVersion().get()

        val urls = dependency.findArtifactUrls(repositories)
        val dependencyTreeEntry = DependencyTreeEntry(
            coord = dependency.id.getRulesJvmExternalCoordinates(),
            directDependencies = dependency.dependencies.map { it.getRulesJvmExternalCoordinates() }.sorted(),
            dependencies = transitiveDependencies.map { it.getRulesJvmExternalCoordinates() }.sorted(),
            url = urls.first(),
            mirrorUrls = urls,
            packages = if (rulesJvmExternalVersion >= SemVer(4, 3)) dependency.computeDependencyPackages()
                .sorted() else null,
            sha256 = Hashing.sha256().hashFile(dependency.jar!!).toZeroPaddedString(64),
            file = "v1/${urls.first().replace("://", "/")}"
        )
        objectMapper.writeValue(outputFile, dependencyTreeEntry)
    }
}

data class DependencyTreeEntry(
    @JsonProperty("coord") val coord: String,
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("directDependencies") val directDependencies: List<String> = emptyList(),
    @JsonProperty("dependencies") val dependencies: List<String> = emptyList(),
    @JsonProperty("url") val url: String,
    @JsonProperty("mirror_urls") val mirrorUrls: List<String> = emptyList(),
    @JsonProperty("packages") @JsonInclude(JsonInclude.Include.NON_NULL) val packages: List<String>? = null,
    @JsonProperty("sha256") val sha256: String
)

data class DependencyTree(
    @JsonProperty("__AUTOGENERATED_FILE_DO_NOT_MODIFY_THIS_FILE_MANUALLY") val oldDependencyTreeSignature: Any = "THERE_IS_NO_DATA_ONLY_ZUUL",
    @JsonProperty("__INPUT_ARTIFACTS_HASH") @JsonInclude(JsonInclude.Include.NON_NULL) val inputArtifactsHash: Int? = null,
    @JsonProperty("__RESOLVED_ARTIFACTS_HASH") @JsonInclude(JsonInclude.Include.NON_NULL) val resolvedArtifactsHash: Int? = null,
    @JsonProperty("conflict_resolution") val conflictResolution: Map<String, String> = emptyMap(),
    @JsonProperty("dependencies") val dependencies: List<DependencyTreeEntry>,
    @JsonProperty("version") val version: String = "0.1.0"
)
