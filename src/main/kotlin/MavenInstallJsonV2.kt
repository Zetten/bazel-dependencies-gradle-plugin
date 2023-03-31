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

typealias MavenInstallJsonV2ArtifactKey = String
typealias MavenInstallJsonV2ArtifactKeyWithClassifier = String
typealias MavenInstallJsonV2RepoUrl = String

private val logger: Logger = LoggerFactory.getLogger(MavenInstallJsonV2::class.java)

class MavenInstallJsonV2(
    workerExecutor: WorkerExecutor,
    rulesJvmExternalVersion: Property<SemVer>,
    temporaryDir: File,
    sortedDependencies: List<ProjectDependency>,
    sortedRepositories: List<String>
) {
    internal val contents: MavenInstallJsonV2Contents

    init {
        // for each dependency, serialise the required attributes to a file for caching

        val snippetFiles = sortedDependencies
            .map { dependency ->
                val snippetFile = temporaryDir.resolve("${dependency.getBazelIdentifier()}.maven_install.snippet")
                workerExecutor.noIsolation().submit(GenerateMavenInstallV2Snippet::class.java) {
                    getOutputFile().set(snippetFile)
                    getDependency().set(dependency)
                    getRepositories().set(sortedRepositories)
                }
                snippetFile
            }

        workerExecutor.await()

        val artifacts = mutableMapOf<MavenInstallJsonV2ArtifactKey, MavenInstallJsonV2Artifact>()
        val dependencies = mutableMapOf<MavenInstallJsonV2ArtifactKey, List<MavenInstallJsonV2ArtifactKey>>()
        val skipped = mutableListOf<MavenInstallJsonV2ArtifactKey>()
        val packages = mutableMapOf<MavenInstallJsonV2ArtifactKey, List<String>>()
        val repositories = mutableMapOf<MavenInstallJsonV2RepoUrl, MutableList<MavenInstallJsonV2ArtifactKey>>()

        // read all snippet files back to objects so we can compute the
        // checksums and emit the complete maven_install.json
        snippetFiles.map { objectMapper.readValue<MavenInstallJsonV2Snippet>(it) }
            .groupBy { "${it.groupId}:${it.artifactId}:${it.version}" }
            .forEach { (coord, snippets) ->
                val key = coordToKey(coord)

                val artifactShasums = mutableMapOf<String, String?>()
                val artifactPackages = mutableListOf<String>()
                val artifactRepositories = mutableSetOf<String>()
                val artifactKeys = mutableListOf<String>()

                snippets.forEach { snippet ->
                    val keyWithClassifier: MavenInstallJsonV2ArtifactKeyWithClassifier

                    if (snippet.classifier == null) {
                        artifactShasums.putAll(snippet.classifierShasums)
                        keyWithClassifier = key
                    } else {
                        artifactShasums.putAll(snippet.classifierShasums.mapKeys { e -> if (e.key == "jar") snippet.classifier else e.key })
                        keyWithClassifier = keyWithClassifier(key, snippet.classifier)
                    }

                    dependencies[keyWithClassifier] = snippet.dependencies
                    artifactPackages += snippet.packages
                    artifactRepositories += snippet.repositories
                    artifactKeys += keyWithClassifier
                    if (snippet.classifierShasums["sources"] != null)
                        artifactKeys += keyWithClassifier(key, "sources")
                }

                artifacts[key] = MavenInstallJsonV2Artifact(artifactShasums, coordToVersion(coord))
                packages[key] = artifactPackages.distinct().sorted()
                artifactRepositories.forEach {
                    repositories.computeIfAbsent(it) { mutableListOf() }.addAll(artifactKeys)
                }
                skipped += artifacts[key]!!.shasums.filter { (_, v) -> v == null }.keys
                    .map { keyWithClassifier(key, it) }
            }

        val resolvedArtifacts = artifacts.toSortedMap()
        val resolvedDependencies = dependencies.toSortedMap()
        val resolvedSkipped = skipped.sorted()
        val resolvedPackages = packages.toSortedMap()
        val resolvedRepositories = repositories.mapValues { (_, v) -> v.sorted() }.toSortedMap()

        this.contents =
            MavenInstallJsonV2Contents(
                inputArtifactsHash = computeDependencyInputsSignature(
                    rulesJvmExternalVersion.get(),
                    sortedDependencies.map { MavenSpec(it) },
                    sortedRepositories
                ),
                resolvedArtifactsHash = resolvedArtifactsHash(
                    resolvedArtifacts,
                    resolvedDependencies,
                    resolvedRepositories
                ),
                artifacts = resolvedArtifacts,
                dependencies = resolvedDependencies,
                skipped = resolvedSkipped,
                packages = resolvedPackages,
                repositories = resolvedRepositories
            )
    }
}

interface GenerateMavenInstallV2SnippetParams : WorkParameters {
    fun getOutputFile(): RegularFileProperty
    fun getDependency(): Property<ProjectDependency>
    fun getRepositories(): ListProperty<String>
}

abstract class GenerateMavenInstallV2Snippet : WorkAction<GenerateMavenInstallV2SnippetParams> {
    private val logger: Logger = LoggerFactory.getLogger(GenerateMavenInstallV2Snippet::class.java)

    override fun execute() {
        val outputFile = parameters.getOutputFile().asFile.get()
        val dependency = parameters.getDependency().get()
        val repositories = parameters.getRepositories().get()

        logger.info("Generating rules_jvm_external dependency tree snippet (v2) for ${dependency.id}")

        val classifierShasums = mutableMapOf<String, String?>()
        classifierShasums["jar"] =
            if (dependency.jar != null) Hashing.sha256().hashFile(dependency.jar).toZeroPaddedString(64)
            else null
        classifierShasums["sources"] =
            if (dependency.srcJar != null) Hashing.sha256().hashFile(dependency.srcJar).toZeroPaddedString(64)
            else null

        val mavenInstallV2Snippet = MavenInstallJsonV2Snippet(
            groupId = dependency.id.group,
            artifactId = dependency.id.name,
            version = dependency.id.version,
            classifier = dependency.classifier,
            classifierShasums = classifierShasums,
            dependencies = dependency.dependencies.map { coordToKey(it.getJvmMavenImportExternalCoordinates()) }
                .sorted(),
            packages = dependency.computeDependencyPackages().sorted(),
            repositories = dependency.findArtifactRepositories(repositories)
        )
        objectMapper.writeValue(outputFile, mavenInstallV2Snippet)
    }
}

data class MavenInstallJsonV2Contents(
    @JsonProperty(
        "__AUTOGENERATED_FILE_DO_NOT_MODIFY_THIS_FILE_MANUALLY",
        index = 0
    ) val oldDependencyTreeSignature: Any = "THERE_IS_NO_DATA_ONLY_ZUUL",
    @JsonProperty("__INPUT_ARTIFACTS_HASH", index = 1) val inputArtifactsHash: Int,
    @JsonProperty("__RESOLVED_ARTIFACTS_HASH", index = 2) val resolvedArtifactsHash: Int,
    @JsonProperty("artifacts", index = 3) val artifacts: Map<MavenInstallJsonV2ArtifactKey, MavenInstallJsonV2Artifact>,
    @JsonProperty(
        "dependencies",
        index = 4
    ) @JsonInclude(content = JsonInclude.Include.NON_EMPTY) val dependencies: Map<MavenInstallJsonV2ArtifactKeyWithClassifier, List<MavenInstallJsonV2ArtifactKeyWithClassifier>>,
    @JsonProperty(
        "skipped",
        index = 5
    ) @JsonInclude(JsonInclude.Include.NON_EMPTY) val skipped: List<MavenInstallJsonV2ArtifactKey>?,
    @JsonProperty(
        "packages",
        index = 6
    ) @JsonInclude(content = JsonInclude.Include.NON_EMPTY) val packages: Map<MavenInstallJsonV2ArtifactKey, List<String>>,
    @JsonProperty(
        "repositories",
        index = 7
    ) val repositories: Map<MavenInstallJsonV2RepoUrl, List<MavenInstallJsonV2ArtifactKeyWithClassifier>>,
    @JsonProperty("version", index = 8) val version: String = "2"
) {
    companion object Factory {
        fun from(file: File): MavenInstallJsonV2Contents = objectMapper.readValue(file)
    }

    fun computeResolvedArtifactsHash(): Int = resolvedArtifactsHash(artifacts, dependencies, repositories)
}

data class MavenInstallJsonV2Artifact(
    @JsonProperty("shasums") val shasums: Map<String, String?>,
    @JsonProperty("version") val version: String
)

data class MavenInstallJsonV2Snippet(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String?,
    val classifierShasums: Map<String, String?>,
    val dependencies: List<MavenInstallJsonV2ArtifactKeyWithClassifier>,
    val packages: List<String>,
    val repositories: List<MavenInstallJsonV2RepoUrl>
)

private fun resolvedArtifactsHash(
    artifacts: Map<MavenInstallJsonV2ArtifactKey, MavenInstallJsonV2Artifact>,
    dependencies: Map<MavenInstallJsonV2ArtifactKey, List<MavenInstallJsonV2ArtifactKey>>,
    repositories: Map<MavenInstallJsonV2RepoUrl, List<MavenInstallJsonV2ArtifactKey>>
): Int {
    val repr = buildString {
        append("{\"artifacts\": {")
        append(artifacts.map { (k, v) ->
            buildString {
                append("\"$k\": {")
                append("\"shasums\": {")
                append(v.shasums.map { (kSum, vSum) ->
                    "\"${kSum}\": ${if (vSum == null) "None" else "\"${vSum}\""}"
                }.joinToString(", "))
                append("}, \"version\": \"${v.version}\"}")
            }
        }.joinToString(", "))
        append("}, \"dependencies\": {")
        append(dependencies.filterValues { it.isNotEmpty() }.map { (k, v) ->
            buildString {
                append("\"$k\": [")
                append(v.joinToString(", ") { "\"$it\"" })
                append("]")
            }
        }.joinToString(", "))
        append("}, \"repositories\": {")
        append(repositories.filterValues { it.isNotEmpty() }.map { (k, v) ->
            buildString {
                append("\"$k\": [")
                append(v.joinToString(", ") { "\"$it\"" })
                append("]")
            }
        }.joinToString(", "))
        append("}}")
    }
    logger.debug(repr)
    return repr.hashCode()
}

private fun coordToKey(coord: String): String = coord.substringBeforeLast(':')
private fun coordToVersion(coord: String): String = coord.substringAfterLast(':')
private fun keyWithClassifier(key: MavenInstallJsonV2ArtifactKey, classifier: String): String =
    if (classifier == "jar") key else "$key:jar:$classifier"