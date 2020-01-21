# Bazel Dependencies Plugin

A Gradle plugin that allows the generation of [Bazel][1] repository rules from
Gradle project dependency configuration.

Features:
* Generates [`java_import_external`][2] rules with transitive dependencies
  mapped to `runtime_deps`
* Wraps generated rules in a function which allows per-dependency exclusion and
  replacement in the Bazel project
* Detects multiple repository URLs for artifact availability
* Detects the most restrictive license for each dependency (using
  [`com.github.jk1.dependency-license-report`][3] plugin)

## Compatibility

* Version 1.0.0 of the plugin should be compatible with most Bazel versions.
* Version 1.1.0 is compatible with Bazel &gt;= 0.23.0 as it generates
  `jvm_maven_import_external` rules making use of the `fetch_sources` attribute
  which did not exist prior to this version.
* Version 1.5.0 provides initial support for [rules_jvm_external][4]. This
  includes the generation of the artifact list for a complete dependency
  closure, evaluated by gradle, which therefore enables support for maven BOMs
  and custom dependency resolution behaviour.
  
  The call to `maven_install` should specify `version_conflict_policy = "pinned"`
  to ensure coursier does not resolve dependencies outside this closure.

## Usage

Applying the plugin provides a single task, `generateWorkspace`. This has some
mandatory configuration parameters to be defined in `build.gradle`:

* `configuration`: The project configuration providing dependencies to be
  resolved
* `outputFile`: The target file for the Bazel repository rules function

Simply running the `generateWorkspace` task will walk the configuration's
dependency tree, detect valid repository URLs, determine the most restrictive
applicable licenses, and emit a file which can be loaded in a Bazel WORKSPACE.

### Optional configuration parameters

* `mode` (default `com.github.zetten.bazeldeps.BazelDependenciesMode.JVM_MAVEN_IMPORT_EXTERNAL`):
  Configures the chosen output mode. `JVM_MAVEN_IMPORT_EXTERNAL` will produce
  a set of `jvm_maven_import_external` repository rules for the required
  artifacts. `RULES_JVM_EXTERNAL` will emit `REPOSITORIES` and `ARTIFACTS`
  attributes which can be imported and passed to `maven_install` from
  [rules_jvm_external][4].
* `strictLicenses` (default `True`): A `Boolean` to control whether
  `generateWorkspace` should fail in the event that a known license level
  cannot be determined.
* `licenseOverrides` (default `{}`): A `Map<String, String>` to set and/or
  override license detection for specific dependency identifiers.
* `dependenciesAttr` (default `"exports"`): A `String` used to set the
  repository rule attribute for dependencies. The default value provides
  Maven-like transitive inclusion by making each artifact fully export its
  dependencies. A convenient alternative may be `runtime_deps` to provide
  transitive dependencies only on the runtime classpath.
* `safeSources` (default `False`): A `Boolean` to control whether 
  `generateWorkspace` should perform a GET request to ensure `fetch_sources`
  is `False` when source jars can't be found in any repository. This may
  slow down the generation but allows safer usage of `fetch_sources = True`
  in the generated WORKSPACE function.
* `sourcesChecksums` (default `False`): A `Boolean` to add determination of
  `srcjar_sha256` attributes in the generated repository rules. This may slow
  down the generation, as Gradle resolves the source jars independently of the
  artifacts, but allows safer usage of `fetch_sources = True` (and reduces
  Bazel's noisy logging of sha256 values which were not provided in the rules).
* `compileOnly` (default empty set): A `Set<String>` of artifact identifiers
  for which the Bazel targets should be marked `neverlink = True`, i.e.
  available only on the compilation classpath, and not at runtime. Currently
  only functional with mode `RULES_JVM_EXTERNAL`.
* `testOnly` (default empty set): A `Set<String>` of artifact identifiers
  for which the Bazel targets should be marked `testonly = True`, i.e.
  available only to targets which are themselves `testonly`, for example to
  avoid leaking test libraries into production artifacts. Currently only
  functional with mode `RULES_JVM_EXTERNAL`, and the `testonly` attribute is
  only supported by rules_jvm_external &gt; 3.1.

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
    configuration = generate
    outputFile = project.buildDir.resolve("java_repositories.bzl")
    licenseOverrides = mapOf(
        Pair("dom4j:dom4j:1.6.1", "notice"), // https://github.com/dom4j/dom4j/blob/master/LICENSE
        Pair("com.google.code.findbugs:jsr305:3.0.2", "restricted") // overrides "notice"
    )
}
```

[1]: https://bazel.build
[2]: https://github.com/bazelbuild/bazel/blob/master/tools/build_defs/repo/java.bzl
[3]: https://github.com/jk1/Gradle-License-Report
[4]: https://github.com/bazelbuild/rules_jvm_external
