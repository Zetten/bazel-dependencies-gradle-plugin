# Bazel Dependencies Plugin

A Gradle plugin that allows the generation of [Bazel][1] repository rules from
Gradle project dependency configuration.

Features:

* Generates [`java_import_external`][2] rules with transitive dependencies
  mapped to `runtime_deps*
* Wraps generated rules in a function which allows per-dependency exclusion and
  replacement in the Bazel project
* Generates [`rules_jvm_external`][4] artifact list and optional version pinning
* Allows re-hashing the pinned `maven_install.json` file after manual
  modifications (e.g. to replace URLs)
* Detects multiple repository URLs for artifact availability
* Detects the most restrictive license for each dependency (using
  [`com.github.jk1.dependency-license-report`][3] plugin)

## Compatibility &amp; upgrade notes

* Version 1.0.0 of the plugin should be compatible with most Bazel versions.
* Version 1.1.0 is compatible with Bazel &gt;= 0.23.0 as it generates
  `jvm_maven_import_external` rules making use of the `fetch_sources` attribute
  which did not exist prior to this version.
* Version 1.5.0 provides initial support for [rules_jvm_external][4]. This
  includes the generation of the artifact list for a complete dependency
  closure, evaluated by gradle, which therefore enables support for maven BOMs
  and custom dependency resolution behaviour.

  The call to `maven_install` should
  specify `version_conflict_policy = "pinned"`
  to ensure coursier does not resolve dependencies outside this closure.
* Version 1.8.0 contains **breaking changes**: properties in the
  `bazelDependencies` use the Gradle Property syntax, instead of assignment.

  Replace configuration like
  ```kotlin
  bazelDependencies {
    configuration = generate
    outputFile = project.buildDir.resolve("java_repositories.bzl")
    strictLicenses = true
  }
  ```
  with
  ```kotlin
  bazelDependencies {
    configuration.set(generate)
    outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
    strictLicenses.set(true)
  }
  ```

  This version also removes the `mode` parameter in favour of explicit task
  naming. The `JVM_MAVEN_IMPORT_EXTERNAL` mode now corresponds to the
  `generateJvmMavenImportExternal` task, while `RULES_JVM_EXTERNAL` is replaced
  by `generateRulesJvmExternal`.

  This version adds support for rehashing the version pinning file
  `maven_install.json`, with a new task: `rehashMavenInstall`.
* Version 2.0.0 adds support for the new checksum format introduced in
  rules_jvm_external. This is managed with a new property to describe the
  version of the rules dependency:
  ```kotlin
  bazelDependencies {
    rulesJvmExternalVersion.set("4.1.0")
  }
  ```

  Additionally, configuration of the `com.github.jk1.dependency-license-report`
  plugin is pulled from the project if the plugin is already applied, enabling
  some tweaking of the discovered license data.
* Version 2.1.0 supports Gradle &ge; 7.4, and supports more
  flexible `compileOnly`/`testOnly` matching by optionally omitting dependency
  versions.
* Version 2.2.1 adds support for the new lockfile hashing  in rules_jvm_external
  version 4.3.
* Version 2.3.0 adds support for the new lockfile format in rules_jvm_external
  version 5.0.

## Usage

Applying the plugin provides three tasks:

* `generateJvmMavenImportExternal`
* `generateRulesJvmExternal`
* `rehashMavenInstall`

The plugin exposes some mandatory configuration parameters to be defined in
`build.gradle` in a `bazelDependencies` block:

* `configuration`: The project configuration providing dependencies to be
  resolved
* `outputFile`: The target file for the Bazel repository rules function

Running the `generateJvmMavenImportExternal` task will walk the configuration's
dependency tree, detect valid repository URLs, determine the most restrictive
applicable licenses, and emit a file which can be loaded in a Bazel WORKSPACE.

Running the `generateRulesJvmExternal` task will walk the configuration's
dependency tree and emit a complete list of all dependency versions, suitable
for loading into the `artifacts` attribute of the `maven_install` repository
rule from `rules_jvm_external`. The `maven_install.json` artifact lock file can
optionally be generated, with correct checksums and valid URLs for each
dependency.

### Optional configuration parameters

* `createMavenInstallJson` (default `True`): A `Boolean` effective in the
  `generateRulesJvmExternal` task, creating a `maven_install.json` file
  alongside the `java_repositories.bzl` file. This JSON file is intended to be
  usable with the `maven_install_json` attribute of `maven_install` from
  [rules_jvm_external][4], for faster and cacheable resolution of Maven
  dependencies.
* `strictLicenses` (default `True`): A `Boolean` to control whether
  `generateJvmMavenImportExternal` should fail in the event that a known license
  level for each dependency cannot be determined.
* `licenseOverrides` (default `{}`): A `Map<String, String>` to set and/or
  override license detection for specific dependency identifiers.
* `dependenciesAttr` (default `"exports"`): A `String` used to set the
  repository rule attribute for dependencies. The default value provides
  Maven-like transitive inclusion by making each artifact fully export its
  dependencies. A convenient alternative may be `runtime_deps` to provide
  transitive dependencies only on the runtime classpath.
* `safeSources` (default `False`): A `Boolean` effective in the
  `generateJvmMavenImportExternal` task to control whether `generateWorkspace`
  should perform a GET request to ensure `fetch_sources` is `False` when source
  jars can't be found in any repository. This may slow down the generation but
  allows safer usage of `fetch_sources = True` in the generated WORKSPACE
  function.
* `sourcesChecksums` (default `False`): A `Boolean` effective in the
  `generateJvmMavenImportExternal` task to add determination of `srcjar_sha256`
  attributes in the generated repository rules. This may slow down the
  generation, as Gradle resolves the source jars independently of the artifacts,
  but allows safer usage of `fetch_sources = True` (and reduces Bazel's noisy
  logging of sha256 values which were not provided in the rules). In the
  `generateRulesJvmExternal` task with `createMavenInstallJson.set(true)`, this
  attribute ensures generation of JSON entries for the sources artifacts.
* `compileOnly` (default empty set): A `Set<String>` of artifact identifiers for
  which the Bazel targets should be marked `neverlink = True`, i.e. available
  only on the compilation classpath, and not at runtime. Currently only
  functional with `generateRulesJvmExternal`.
* `testOnly` (default empty set): A `Set<String>` of artifact identifiers for
  which the Bazel targets should be marked `testonly = True`, i.e. available
  only to targets which are themselves `testonly`, for example to avoid leaking
  test libraries into production artifacts. Currently only functional with
  `generateRulesJvmExternal`, and the `testonly` attribute is only supported by
  rules_jvm_external &gt; 3.1.
* `rulesJvmExternalVersion` (default `"4.0"`): A `String` describing the target
  version of rules_jvm_external. This controls the output format of
  `maven_install.json`.

## Example (kotlin-dsl)

```kotlin
plugins {
    base
    id("bazel-dependencies-plugin")
}

repositories {
    jcenter()
}

val generate by configurations.creating

dependencies {
    generate("com.google.guava:guava:26.0-jre")
    generate("dom4j:dom4j:1.6.1")
}

bazelDependencies {
    configuration.set(generate)
    outputFile.set(project.buildDir.resolve("java_repositories.bzl"))
    licenseOverrides.set(
        mapOf(
            Pair(
                "dom4j:dom4j:1.6.1",
                "notice"
            ), // https://github.com/dom4j/dom4j/blob/master/LICENSE
            Pair(
                "com.google.code.findbugs:jsr305:3.0.2",
                "restricted"
            ) // overrides "notice"
        )
    )
}
```

[1]: https://bazel.build

[2]: https://github.com/bazelbuild/bazel/blob/master/tools/build_defs/repo/java.bzl

[3]: https://github.com/jk1/Gradle-License-Report

[4]: https://github.com/bazelbuild/rules_jvm_external
