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

Version 1.0.0 of the plugin should be compatible with most Bazel versions.
Version 1.1.0 is compatible with Bazel &gt;= 0.23.0 as it generates
`jvm_maven_import_external` rules making use of the `fetch_sources` attribute
which did not exist prior to this version.

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