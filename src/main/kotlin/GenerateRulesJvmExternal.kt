package com.github.zetten.bazeldeps

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.swiftzer.semver.SemVer
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.SortedMap
import javax.inject.Inject


private val indenter = DefaultIndenter()
private val prettyPrinter = DefaultPrettyPrinter()
    .withArrayIndenter(indenter)
    .withObjectIndenter(indenter)
val objectMapper: JsonMapper = JsonMapper.builder()
    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    .enable(SerializationFeature.INDENT_OUTPUT)
    .addModule(KotlinModule.Builder().build())
    .defaultPrettyPrinter(prettyPrinter)
    .build()

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
            val contents: Any
            if (rulesJvmExternalVersion.get() >= SemVer(5, 0)) {
                contents = MavenInstallJsonV2(
                    workerExecutor,
                    rulesJvmExternalVersion,
                    temporaryDir,
                    sortedDependencies,
                    sortedRepositories
                ).contents
            } else {
                contents = MavenInstallJsonV1(
                    workerExecutor,
                    rulesJvmExternalVersion,
                    temporaryDir,
                    sortedDependencies,
                    sortedRepositories
                ).contents
            }
            objectMapper.writeValue(mavenInstallJsonFile.get(), contents)
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

// Implementation of https://github.com/bazelbuild/rules_jvm_external/blob/8feca27d7efed5a3343f8dbfe1199987598ca778/coursier.bzl#L205
internal fun computeDependencyInputsSignature(
    rulesJvmExternalSemVer: SemVer,
    dependencies: List<MavenSpec>,
    repositories: List<String>
): Int {
    val signatureInputs = dependencies.map { dep ->
        val depAttrs: SortedMap<String, Any> = objectMapper.readValue(objectMapper.writeValueAsBytes(dep))
        depAttrs.entries.joinToString(":") { "${it.key}=${it.value}" }
    }
    val artifactsString = "[${signatureInputs.sorted().joinToString(", ") { "\"$it\"" }}]"

    return if (rulesJvmExternalSemVer >= SemVer(4, 3)) {
        val repositoriesString = "[${repositories.joinToString(", ") { "\"{ \\\"repo_url\\\": \\\"$it\\\" }\"" }}]"
        artifactsString.hashCode() xor repositoriesString.hashCode()
    } else {
        artifactsString.hashCode()
    }
}