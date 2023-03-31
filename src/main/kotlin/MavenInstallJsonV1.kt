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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.SortedMap

class MavenInstallJsonV1(
    workerExecutor: WorkerExecutor,
    rulesJvmExternalVersion: Property<SemVer>,
    temporaryDir: File,
    sortedDependencies: List<ProjectDependency>,
    sortedRepositories: List<String>
) {
    internal val contents: MavenInstallJsonV1Contents

    init {
        val snippetFiles = sortedDependencies
            .flatMap {
                if (it.srcJar != null) {
                    listOf(
                        it,
                        it.copy(
                            classifier = "sources",
                            jar = it.srcJar,
                            dependencies = it.dependencies.map { it.copy(classifier = "sources") }.toSet(),
                            allDependencies = it.allDependencies.map { it.copy(classifier = "sources") }.toSet()
                        )
                    )
                } else {
                    listOf(it)
                }
            }
            .map { dependency ->
                val snippetFile = temporaryDir.resolve("${dependency.getBazelIdentifier()}.maven_install.snippet")
                workerExecutor.noIsolation().submit(GenerateDependencyTreeSnippet::class.java) {
                    getOutputFile().set(snippetFile)
                    getDependency().set(dependency)
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
                    dependencies.map { MavenSpec(it) },
                    repositories
                )
            )
        }
    )

    companion object Factory {
        fun from(file: File): MavenInstallJsonV1Contents = objectMapper.readValue(file)
    }
}

interface DependencyTreeMapperParams : WorkParameters {
    fun getOutputFile(): RegularFileProperty
    fun getDependency(): Property<ProjectDependency>
    fun getRepositories(): ListProperty<String>
    fun getRulesJvmExternalVersion(): Property<SemVer>
}

abstract class GenerateDependencyTreeSnippet : WorkAction<DependencyTreeMapperParams> {
    private val logger: Logger = LoggerFactory.getLogger(GenerateDependencySnippet::class.java)

    override fun execute() {
        val outputFile = parameters.getOutputFile().asFile.get()
        val dependency = parameters.getDependency().get()
        val repositories = parameters.getRepositories().get()
        val rulesJvmExternalVersion = parameters.getRulesJvmExternalVersion().get()

        logger.info("Generating rules_jvm_external dependency tree snippet for ${dependency.id}")

        val urls = dependency.findArtifactUrls(repositories)
        val dependencyTreeEntry = DependencyTreeEntry(
            coord = dependency.getJvmMavenImportExternalCoordinates(),
            directDependencies = dependency.dependencies.map { it.getJvmMavenImportExternalCoordinates() }.sorted(),
            dependencies = dependency.allDependencies.map { it.getJvmMavenImportExternalCoordinates() }.sorted(),
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

// Implementation of https://github.com/bazelbuild/rules_jvm_external/blob/030ea9ef8e4ea491fed13de1771e225eb5a52d18/coursier.bzl#L120
internal fun computeDependencyTreeSignature(dependencies: List<DependencyTreeEntry>): Int {
    val signatureInputs: List<String> = dependencies.map { dep ->
        var uniq = arrayOf(dep.coord)
        if (dep.file != null) {
            uniq += dep.sha256
            uniq += dep.file
            uniq += dep.url
        }
        if (dep.dependencies.isNotEmpty()) {
            uniq += dep.dependencies.joinToString(",")
        }
        uniq.joinToString(":")
    }
    val signatureString = "[${signatureInputs.sorted().joinToString(", ") { "\"$it\"" }}]"
    return signatureString.hashCode()
}