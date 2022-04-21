package com.github.zetten.bazeldeps

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.swiftzer.semver.SemVer
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.hash.Hashing
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.SortedMap
import javax.inject.Inject

private val indenter = DefaultIndenter()
private val prettyPrinter = DefaultPrettyPrinter()
    .withArrayIndenter(indenter)
    .withObjectIndenter(indenter)
private val objectMapper = ObjectMapper()
    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    .enable(SerializationFeature.INDENT_OUTPUT)
    .registerModule(KotlinModule())
    .setDefaultPrettyPrinter(
        prettyPrinter
    )

@CacheableTask
open class GenerateRulesJvmExternal @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    @Input
    val dependencies: SetProperty<ProjectDependency> = project.objects.setProperty()

    @Input
    val repositories: ListProperty<String> = project.objects.listProperty()

    @Input
    val createMavenInstallJson: Property<Boolean> = project.objects.property<Boolean>().convention(true)

    @Input
    val rulesJvmExternalVersion: Property<SemVer> = project.objects.property<SemVer>().convention(SemVer(4, 0))

    @OutputFile
    val outputFile: Property<File> = project.objects.property()

    @OutputFile
    val mavenInstallJsonFile: Property<File> = project.objects.property()

    @TaskAction
    fun generateWorkspace() {
        logger.warn("Generating Bazel rules_jvm_external attributes for {} dependencies", dependencies.get().size)

        outputFile.get().parentFile.mkdirs()

        val sortedRepositories = repositories.get().sorted()
        val sortedDependencies = dependencies.get().sorted()

        outputFile.get().writeText(
            """
            |load("@rules_jvm_external//:specs.bzl", "maven")
            |
            |REPOSITORIES = [
            |${sortedRepositories.joinToString("\n") { "    \"$it\"," }}
            |]
            |
            |ARTIFACTS = [
            |${sortedDependencies.joinToString("\n") { artifactBlock(it) }}
            |]
            |""".trimMargin()
        )

        if (createMavenInstallJson.get()) {
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
                    }
                    snippetFile
                }

            workerExecutor.await()

            // read all snippet files back to objects so we can compute the
            // checksums and emit the complete maven_install.json
            val dependencyTreeEntries: List<DependencyTreeEntry> = snippetFiles.map(objectMapper::readValue)
            val mavenInstallJson =
                MavenInstallJson(rulesJvmExternalVersion.get(), sortedDependencies, dependencyTreeEntries)
            mavenInstallJson.write(mavenInstallJsonFile.get())
        }
    }

    private fun artifactBlock(dep: ProjectDependency): String {
        val params = mutableListOf("\"${dep.id.group}\"", "\"${dep.id.name}\"", "\"${dep.id.version}\"")
        if (dep.jar!!.extension != "jar") params += "packaging = \"${dep.jar.extension}\""
        if (dep.classifier != null) params += "classifier = \"${dep.classifier}\""
        if (dep.overrideLicenseTypes != null) params += "override_license_types = [${
            dep.overrideLicenseTypes.sorted().joinToString(", ") { "\"$it\"" }
        }]"
        if (dep.neverlink) params += "neverlink = True"
        if (dep.testonly) params += "testonly = True"

        return """
        |    maven.artifact(${params.joinToString(", ")}),
        """.trimMargin()
    }
}

interface DependencyTreeMapperParams : WorkParameters {
    fun getOutputFile(): RegularFileProperty
    fun getDependency(): Property<ProjectDependency>
    fun getRepositories(): ListProperty<String>
}

abstract class GenerateDependencyTreeSnippet : WorkAction<DependencyTreeMapperParams> {
    private val logger: Logger = LoggerFactory.getLogger(GenerateDependencySnippet::class.java)

    override fun execute() {
        val outputFile = parameters.getOutputFile().asFile.get()
        val dependency = parameters.getDependency().get()
        val repositories = parameters.getRepositories().get()

        logger.info("Generating rules_jvm_external dependency tree snippet for ${dependency.id}")

        val urls = dependency.findArtifactUrls(repositories)
        val dependencyTreeEntry = DependencyTreeEntry(
            coord = dependency.getJvmMavenImportExternalCoordinates(),
            directDependencies = dependency.dependencies.map { it.getJvmMavenImportExternalCoordinates() }.sorted(),
            dependencies = dependency.allDependencies.map { it.getJvmMavenImportExternalCoordinates() }.sorted(),
            url = urls.first(),
            mirrorUrls = urls,
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
    @JsonProperty("sha256") val sha256: String
)

data class MavenInstallJson(
    @JsonProperty("dependency_tree") val dependencyTree: DependencyTree,
) {
    constructor(
        rulesJvmExternalSemVer: SemVer,
        dependencies: List<ProjectDependency>,
        dependencyTreeEntries: List<DependencyTreeEntry>
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
                inputArtifactsHash = computeDependencyInputsSignature(dependencies)
            )
        }
    )

    companion object Factory {
        fun from(file: File): MavenInstallJson = objectMapper.readValue(file)
    }

    fun write(file: File) = objectMapper.writeValue(file, this)
}

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

// Implementation of https://github.com/bazelbuild/rules_jvm_external/blob/8feca27d7efed5a3343f8dbfe1199987598ca778/coursier.bzl#L205
internal fun computeDependencyInputsSignature(dependencies: List<ProjectDependency>): Int {
    val signatureInputs = dependencies.map { dep ->
        val depAttrs: SortedMap<String, Any> = objectMapper.readValue(objectMapper.writeValueAsBytes(MavenSpec(dep)))
        depAttrs.entries.joinToString(":") { "${it.key}=${it.value}" }
    }
    val signatureString = "[${signatureInputs.sorted().joinToString(", ") { "\"$it\"" }}]"
    return signatureString.hashCode()
}

data class MavenSpec(
    @JsonProperty("group") val group: String,
    @JsonProperty("artifact") val artifact: String,
    @JsonProperty("version") val version: String,
    @JsonProperty("packaging") @JsonInclude(JsonInclude.Include.NON_NULL) val packaging: String? = null,
    @JsonProperty("classifier") @JsonInclude(JsonInclude.Include.NON_NULL) val classifier: String? = null,
    @JsonProperty("override_license_types") @JsonInclude(JsonInclude.Include.NON_NULL) val overrideLicenseTypes: List<String>? = null,
    @JsonProperty("exclusions") @JsonInclude(JsonInclude.Include.NON_NULL) val exclusions: List<MavenExclusionSpec>? = null,
    @JsonProperty("neverlink") @JsonInclude(JsonInclude.Include.NON_NULL) val neverlink: String? = null,
    @JsonProperty("testonly") @JsonInclude(JsonInclude.Include.NON_NULL) val testonly: String? = null
) {
    constructor(dep: ProjectDependency) : this(
        dep.id.group,
        dep.id.name,
        dep.id.version,
        dep.packaging,
        dep.classifier,
        dep.overrideLicenseTypes,
        null /* exclusions are managed directly in maven_install.json */,
        if (dep.neverlink) "True" else null,
        if (dep.testonly) "True" else null
    )
}

data class MavenExclusionSpec(
    @JsonProperty("group") val group: String,
    @JsonProperty("artifact") val artifact: String,
)