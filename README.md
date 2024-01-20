# Bazel Dependencies Plugin

A Gradle plugin that allows the generation of [Bazel][1] repository rules from
Gradle project dependency configuration.

Features:

* Generates [`jvm_import_external`][2] rules with transitive dependencies
  mapped to compile and runtime scopes
* Wraps generated rules in a function which allows per-dependency exclusion and
  replacement in the Bazel project
* Generates [`rules_jvm_external`][4] artifact list, respecting Gradle
  dependency exclusion rules, and supporting optional version pinning
  (`maven_install.json`)
* Allows re-hashing the pinned `maven_install.json` file after manual
  modifications (e.g. to replace URLs)
* Detects multiple repository URLs for artifact availability

## Compatibility &amp; upgrade notes

* Version 3.0.0 refactors the dependency tree calculation, and tracks usage
  scope (api/runtime). This is currently used in `jvm_import_external`
  attributes, but is not supported by `rules_jvm_external`. Dependency cycles
  are now detected and cause an error, but may be handled by transitive
  dependency exclusions, which are now propagated from the Gradle project to
  the generated Bazel configuration.

  **Breaking changes:**
    * The Gradle project extension (configuration API) has been refactored and
      will likely require changes in your project.
    * Dependency source artifact resolution is now included by default, and
      cannot be disabled.
    * Dependency license detection is no longer provided and `license`
      attributes are no longer set (per the [Bazel
      docs](https://bazel.build/reference/be/common-definitions#typical-attributes)
      "This is part of a deprecated licensing API that Bazel no longer uses.
      Don't use this.").
* Version 2.3.0 adds support for the new lockfile format in rules_jvm_external
  version 5.0.
* Version 2.2.1 adds support for the new lockfile hashing in rules_jvm_external
  version 4.3.
* Version 2.1.0 supports Gradle &ge; 7.4, and supports more
  flexible `compileOnly`/`testOnly` matching by optionally omitting dependency
  versions.
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
* Version 1.5.0 provides initial support for [rules_jvm_external][4]. This
  includes the generation of the artifact list for a complete dependency
  closure, evaluated by gradle, which therefore enables support for maven BOMs
  and custom dependency resolution behaviour.

  The call to `maven_install` should
  specify `version_conflict_policy = "pinned"` to ensure coursier does not
  resolve dependencies outside this closure.
* Version 1.1.0 is compatible with Bazel &gt;= 0.23.0 as it generates
  `jvm_maven_import_external` rules making use of the `fetch_sources` attribute
  which did not exist prior to this version.
* Version 1.0.0 of the plugin should be compatible with most Bazel versions.

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

* `neverlink` (default empty set): A `Set<String>` of artifact identifiers for
  which the Bazel targets should be marked `neverlink = True`, i.e. available
  only on the compilation classpath, and not at runtime. Currently only
  functional with `generateRulesJvmExternal`.
* `testonly` (default empty set): A `Set<String>` of artifact identifiers for
  which the Bazel targets should be marked `testonly = True`, i.e. available
  only to targets which are themselves `testonly`, for example to avoid leaking
  test libraries into production artifacts. Currently only functional with
  `generateRulesJvmExternal`, and the `testonly` attribute is only supported by
  rules_jvm_external &gt; 3.1.
* `jvmImportExternal.createAggregatorRepo` (default `true`): If true, an
  aggregate repository rule is created with all imported dependencies, to
  provide consistency with the `rules_jvm_external` approach. A dependency
  available at `@com_example_somedep` will be additionally aliased
  from `@maven//:com_example_somedep`. The name of this rule (`maven`) may be
  set when instantiating the repo in the Bazel WORKSPACE file.
* `rulesJvmExternal.version` (default `"6.0"`): A `String` describing the target
  version of rules_jvm_external. This controls the output format
  of `maven_install.json`.
* `rulesJvmExternal.createMavenInstallJson` (default `true`): If true, the
  `generateRulesJvmExternal` task creates a `maven_install.json` lock file
  alongside the `java_repositories.bzl` file. This JSON file is intended to be
  usable with the `maven_install_json` attribute of `maven_install` from
  [rules_jvm_external][4], for pinned versioning and faster and cacheable
  resolution of Maven dependencies.

## Example (kotlin-dsl)

```kotlin
plugins {
    `java-base`
    id("com.github.zetten.bazel-dependencies-plugin")
}

repositories {
    mavenCentral()
}

val generate by configurations.creating {
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    generate(enforcedPlatform("io.grpc:grpc-bom:1.61.0"))
    generate("io.grpc:grpc-core") {
        exclude("io.grpc", "grpc-util")
    }
    generate("com.google.guava:guava:33.0.0-jre")
}

bazelDependencies {
    configuration = generate
    outputFile = project.layout.buildDirectory.file("java_repositories.bzl")
    rulesJvmExternal {
        version = "5.0"
    }
}
```

[1]: https://bazel.build

[2]: https://github.com/bazelbuild/bazel/blob/master/tools/build_defs/repo/java.bzl

[3]: https://github.com/jk1/Gradle-License-Report

[4]: https://github.com/bazelbuild/rules_jvm_external
