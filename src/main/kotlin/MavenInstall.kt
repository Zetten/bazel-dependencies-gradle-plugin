package com.github.zetten.bazeldeps

import net.swiftzer.semver.SemVer

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
internal fun computeDependencyInputsSignature(
    rulesJvmExternalSemVer: SemVer,
    dependencies: List<ProjectDependency>,
    repositories: List<String>
): Int {
    val signatureInputs = dependencies.map { dep ->
        buildList {
            add("artifact=${dep.id.name}")
            if (dep.exclusions.isNotEmpty()) add(
                "exclusions=[${
                    dep.exclusions.joinToString(", ") { "{\\\"group\\\": \\\"${it.group}\\\", \\\"artifact\\\": \\\"${it.artifact}\\\"}" }
                }]"
            )
            add("group=${dep.id.group}")
            if (dep.neverlink) add("neverlink=True")
            if (dep.testonly) add("testonly=True")
            add("version=${dep.id.version}")
        }.joinToString(":")
    }

    val artifactsString = "[${signatureInputs.sorted().joinToString(", ") { "\"$it\"" }}]"
    val repositoriesString = "[${repositories.joinToString(", ") { "\"{ \\\"repo_url\\\": \\\"$it\\\" }\"" }}]"

    return when {
        rulesJvmExternalSemVer >= SemVer(4, 3) -> artifactsString.hashCode() xor repositoriesString.hashCode()
        else -> artifactsString.hashCode()
    }
}

internal fun resolvedArtifactsHash(
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
        append(mapToHashableString(dependencies))
        append("}, \"repositories\": {")
        append(mapToHashableString(repositories))
        append("}}")
    }
    return repr.hashCode()
}

private fun mapToHashableString(collection: Map<MavenInstallJsonV2RepoUrl, List<MavenInstallJsonV2ArtifactKey>>) =
    collection.filterValues { it.isNotEmpty() }.map { (k, v) ->
        buildString {
            append("\"$k\": [")
            append(v.joinToString(", ") { "\"$it\"" })
            append("]")
        }
    }.joinToString(", ")
