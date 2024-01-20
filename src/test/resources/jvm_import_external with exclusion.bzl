load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_import_external")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")

def _replace_dependencies(dependencies, replacements):
    new_dependencies = depset()
    for dep in dependencies:
        if dep in replacements.keys():
            new_dependencies = depset(transitive = [new_dependencies, depset(direct = replacements.get(dep))])
        else:
            new_dependencies = depset(transitive = [new_dependencies, depset(direct = [dep])])
    return new_dependencies.to_list()

def java_repositories(replacements = {}, fetch_sources = True):
    # Load the default and neverlink versions of each dependency
    com_google_android_annotations(replacements, fetch_sources)
    com_google_code_findbugs_jsr305(replacements, fetch_sources)
    com_google_code_gson_gson(replacements, fetch_sources)
    com_google_errorprone_error_prone_annotations(replacements, fetch_sources)
    com_google_guava_failureaccess(replacements, fetch_sources)
    com_google_guava_guava(replacements, fetch_sources)
    com_google_guava_listenablefuture(replacements, fetch_sources)
    com_google_j2objc_j2objc_annotations(replacements, fetch_sources)
    io_grpc_grpc_api(replacements, fetch_sources)
    io_grpc_grpc_context(replacements, fetch_sources)
    io_grpc_grpc_core(replacements, fetch_sources)
    io_perfmark_perfmark_api(replacements, fetch_sources)
    org_checkerframework_checker_qual(replacements, fetch_sources)
    org_codehaus_mojo_animal_sniffer_annotations(replacements, fetch_sources)

def com_google_android_annotations(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "com_google_android_annotations",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/android/annotations/4.1.1.4/annotations-4.1.1.4.jar"],
        artifact_sha256 = "ba734e1e84c09d615af6a09d33034b4f0442f8772dec120efb376d86a565ae15",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/android/annotations/4.1.1.4/annotations-4.1.1.4-sources.jar"],
        srcjar_sha256 = "e9b667aa958df78ea1ad115f7bbac18a5869c3128b1d5043feb360b0cfce9d40",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "com_google_android_annotations_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/android/annotations/4.1.1.4/annotations-4.1.1.4.jar"],
        artifact_sha256 = "ba734e1e84c09d615af6a09d33034b4f0442f8772dec120efb376d86a565ae15",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/android/annotations/4.1.1.4/annotations-4.1.1.4-sources.jar"],
        srcjar_sha256 = "e9b667aa958df78ea1ad115f7bbac18a5869c3128b1d5043feb360b0cfce9d40",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def com_google_code_findbugs_jsr305(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "com_google_code_findbugs_jsr305",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar"],
        artifact_sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2-sources.jar"],
        srcjar_sha256 = "1c9e85e272d0708c6a591dc74828c71603053b48cc75ae83cce56912a2aa063b",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "com_google_code_findbugs_jsr305_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar"],
        artifact_sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2-sources.jar"],
        srcjar_sha256 = "1c9e85e272d0708c6a591dc74828c71603053b48cc75ae83cce56912a2aa063b",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def com_google_code_gson_gson(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "com_google_code_gson_gson",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"],
        artifact_sha256 = "4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1-sources.jar"],
        srcjar_sha256 = "eee1cc5c1f4267ee194cc245777e68084738ef390acd763354ce0ff6bfb7bcc1",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "com_google_code_gson_gson_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"],
        artifact_sha256 = "4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1-sources.jar"],
        srcjar_sha256 = "eee1cc5c1f4267ee194cc245777e68084738ef390acd763354ce0ff6bfb7bcc1",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def com_google_errorprone_error_prone_annotations(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "com_google_errorprone_error_prone_annotations",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/errorprone/error_prone_annotations/2.23.0/error_prone_annotations-2.23.0.jar"],
        artifact_sha256 = "ec6f39f068b6ff9ac323c68e28b9299f8c0a80ca512dccb1d4a70f40ac3ec054",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/errorprone/error_prone_annotations/2.23.0/error_prone_annotations-2.23.0-sources.jar"],
        srcjar_sha256 = "5b4504609bb93d3c24b87cd839cf0bb7d878135d0a917a05081d0dc9b2a9973f",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "com_google_errorprone_error_prone_annotations_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/errorprone/error_prone_annotations/2.23.0/error_prone_annotations-2.23.0.jar"],
        artifact_sha256 = "ec6f39f068b6ff9ac323c68e28b9299f8c0a80ca512dccb1d4a70f40ac3ec054",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/errorprone/error_prone_annotations/2.23.0/error_prone_annotations-2.23.0-sources.jar"],
        srcjar_sha256 = "5b4504609bb93d3c24b87cd839cf0bb7d878135d0a917a05081d0dc9b2a9973f",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def com_google_guava_failureaccess(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "com_google_guava_failureaccess",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar"],
        artifact_sha256 = "8a8f81cf9b359e3f6dfa691a1e776985c061ef2f223c9b2c80753e1b458e8064",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2-sources.jar"],
        srcjar_sha256 = "dd3bfa5e2ec5bc5397efb2c3cef044c192313ff77089573667ff97a60c6978e0",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "com_google_guava_failureaccess_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar"],
        artifact_sha256 = "8a8f81cf9b359e3f6dfa691a1e776985c061ef2f223c9b2c80753e1b458e8064",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2-sources.jar"],
        srcjar_sha256 = "dd3bfa5e2ec5bc5397efb2c3cef044c192313ff77089573667ff97a60c6978e0",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def com_google_guava_guava(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "com_google_guava_guava",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre.jar"],
        artifact_sha256 = "f4d85c3e4d411694337cb873abea09b242b664bb013320be6105327c45991537",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre-sources.jar"],
        srcjar_sha256 = "0c17d911785e8a606d091aa6740d6d520f307749c2bddf6e35066d52fe0036e5",
        deps = _replace_dependencies([
            "@com_google_code_findbugs_jsr305",
            "@com_google_errorprone_error_prone_annotations",
            "@com_google_guava_failureaccess",
            "@com_google_guava_listenablefuture",
            "@com_google_j2objc_j2objc_annotations_neverlink",
            "@org_checkerframework_checker_qual",
        ], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "com_google_guava_guava_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre.jar"],
        artifact_sha256 = "f4d85c3e4d411694337cb873abea09b242b664bb013320be6105327c45991537",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre-sources.jar"],
        srcjar_sha256 = "0c17d911785e8a606d091aa6740d6d520f307749c2bddf6e35066d52fe0036e5",
        deps = _replace_dependencies([
            "@com_google_code_findbugs_jsr305",
            "@com_google_errorprone_error_prone_annotations",
            "@com_google_guava_failureaccess",
            "@com_google_guava_listenablefuture",
            "@com_google_j2objc_j2objc_annotations_neverlink",
            "@org_checkerframework_checker_qual",
        ], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def com_google_guava_listenablefuture(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "com_google_guava_listenablefuture",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar"],
        artifact_sha256 = "b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "com_google_guava_listenablefuture_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar"],
        artifact_sha256 = "b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def com_google_j2objc_j2objc_annotations(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "com_google_j2objc_j2objc_annotations",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/j2objc/j2objc-annotations/2.8/j2objc-annotations-2.8.jar"],
        artifact_sha256 = "f02a95fa1a5e95edb3ed859fd0fb7df709d121a35290eff8b74dce2ab7f4d6ed",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/j2objc/j2objc-annotations/2.8/j2objc-annotations-2.8-sources.jar"],
        srcjar_sha256 = "7413eed41f111453a08837f5ac680edded7faed466cbd35745e402e13f4cc3f5",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "com_google_j2objc_j2objc_annotations_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/com/google/j2objc/j2objc-annotations/2.8/j2objc-annotations-2.8.jar"],
        artifact_sha256 = "f02a95fa1a5e95edb3ed859fd0fb7df709d121a35290eff8b74dce2ab7f4d6ed",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/com/google/j2objc/j2objc-annotations/2.8/j2objc-annotations-2.8-sources.jar"],
        srcjar_sha256 = "7413eed41f111453a08837f5ac680edded7faed466cbd35745e402e13f4cc3f5",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def io_grpc_grpc_api(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "io_grpc_grpc_api",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/io/grpc/grpc-api/1.61.0/grpc-api-1.61.0.jar"],
        artifact_sha256 = "30f314fd84e03053d07b4ddf84bcc5889cf30aac219bb1d08d8fc2f8c54f4255",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/io/grpc/grpc-api/1.61.0/grpc-api-1.61.0-sources.jar"],
        srcjar_sha256 = "857004e81fc269c60ccbe810fb9be529a957677785c676520bf1e1b42e28734a",
        deps = _replace_dependencies([
            "@com_google_code_findbugs_jsr305",
            "@com_google_errorprone_error_prone_annotations",
        ], replacements),
        runtime_deps = _replace_dependencies(["@com_google_guava_guava"], replacements),
    )

    maybe(
        jvm_import_external,
        name = "io_grpc_grpc_api_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/io/grpc/grpc-api/1.61.0/grpc-api-1.61.0.jar"],
        artifact_sha256 = "30f314fd84e03053d07b4ddf84bcc5889cf30aac219bb1d08d8fc2f8c54f4255",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/io/grpc/grpc-api/1.61.0/grpc-api-1.61.0-sources.jar"],
        srcjar_sha256 = "857004e81fc269c60ccbe810fb9be529a957677785c676520bf1e1b42e28734a",
        deps = _replace_dependencies([
            "@com_google_code_findbugs_jsr305",
            "@com_google_errorprone_error_prone_annotations",
        ], replacements),
        runtime_deps = _replace_dependencies(["@com_google_guava_guava"], replacements),
        neverlink = True,
    )

def io_grpc_grpc_context(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "io_grpc_grpc_context",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/io/grpc/grpc-context/1.61.0/grpc-context-1.61.0.jar"],
        artifact_sha256 = "21f4ac4c5e9517a2d0282438fd83f0659e8cac226bf05e0860f84c899bceab61",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/io/grpc/grpc-context/1.61.0/grpc-context-1.61.0-sources.jar"],
        srcjar_sha256 = "8bede6b622dd0d7dd7c5a69e97e01c54eb0e1a4e2cdc2a67f3bf0b2e275c6fa4",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies(["@io_grpc_grpc_api"], replacements),
    )

    maybe(
        jvm_import_external,
        name = "io_grpc_grpc_context_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/io/grpc/grpc-context/1.61.0/grpc-context-1.61.0.jar"],
        artifact_sha256 = "21f4ac4c5e9517a2d0282438fd83f0659e8cac226bf05e0860f84c899bceab61",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/io/grpc/grpc-context/1.61.0/grpc-context-1.61.0-sources.jar"],
        srcjar_sha256 = "8bede6b622dd0d7dd7c5a69e97e01c54eb0e1a4e2cdc2a67f3bf0b2e275c6fa4",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies(["@io_grpc_grpc_api"], replacements),
        neverlink = True,
    )

def io_grpc_grpc_core(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "io_grpc_grpc_core",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/io/grpc/grpc-core/1.61.0/grpc-core-1.61.0.jar"],
        artifact_sha256 = "02288f49476080c54cf1b66e2074171f83e22a9bdb33e82ed27b3083abb790b9",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/io/grpc/grpc-core/1.61.0/grpc-core-1.61.0-sources.jar"],
        srcjar_sha256 = "d0b0bc7cc9a3afbda56bcd52c4d9a2439080c22449ee31d5a06ba4d2fe158f61",
        deps = _replace_dependencies(["@io_grpc_grpc_api"], replacements),
        runtime_deps = _replace_dependencies([
            "@com_google_android_annotations",
            "@com_google_code_gson_gson",
            "@com_google_errorprone_error_prone_annotations",
            "@com_google_guava_guava",
            "@io_grpc_grpc_context",
            "@io_perfmark_perfmark_api",
            "@org_codehaus_mojo_animal_sniffer_annotations",
        ], replacements),
    )

    maybe(
        jvm_import_external,
        name = "io_grpc_grpc_core_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/io/grpc/grpc-core/1.61.0/grpc-core-1.61.0.jar"],
        artifact_sha256 = "02288f49476080c54cf1b66e2074171f83e22a9bdb33e82ed27b3083abb790b9",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/io/grpc/grpc-core/1.61.0/grpc-core-1.61.0-sources.jar"],
        srcjar_sha256 = "d0b0bc7cc9a3afbda56bcd52c4d9a2439080c22449ee31d5a06ba4d2fe158f61",
        deps = _replace_dependencies(["@io_grpc_grpc_api"], replacements),
        runtime_deps = _replace_dependencies([
            "@com_google_android_annotations",
            "@com_google_code_gson_gson",
            "@com_google_errorprone_error_prone_annotations",
            "@com_google_guava_guava",
            "@io_grpc_grpc_context",
            "@io_perfmark_perfmark_api",
            "@org_codehaus_mojo_animal_sniffer_annotations",
        ], replacements),
        neverlink = True,
    )

def io_perfmark_perfmark_api(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "io_perfmark_perfmark_api",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/io/perfmark/perfmark-api/0.26.0/perfmark-api-0.26.0.jar"],
        artifact_sha256 = "b7d23e93a34537ce332708269a0d1404788a5b5e1949e82f5535fce51b3ea95b",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/io/perfmark/perfmark-api/0.26.0/perfmark-api-0.26.0-sources.jar"],
        srcjar_sha256 = "7379e0fef0c32d69f3ebae8f271f426fc808613f1cfbc29e680757f348ba8aa4",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "io_perfmark_perfmark_api_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/io/perfmark/perfmark-api/0.26.0/perfmark-api-0.26.0.jar"],
        artifact_sha256 = "b7d23e93a34537ce332708269a0d1404788a5b5e1949e82f5535fce51b3ea95b",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/io/perfmark/perfmark-api/0.26.0/perfmark-api-0.26.0-sources.jar"],
        srcjar_sha256 = "7379e0fef0c32d69f3ebae8f271f426fc808613f1cfbc29e680757f348ba8aa4",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def org_checkerframework_checker_qual(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "org_checkerframework_checker_qual",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/org/checkerframework/checker-qual/3.41.0/checker-qual-3.41.0.jar"],
        artifact_sha256 = "2f9f245bf68e4259d610894f2406dc1f6363dc639302bd566e8272e4f4541172",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/org/checkerframework/checker-qual/3.41.0/checker-qual-3.41.0-sources.jar"],
        srcjar_sha256 = "8308220bbdd4e12b49fa06a91de685faf9cc1a376464478c80845be3e87b7d4f",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "org_checkerframework_checker_qual_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/org/checkerframework/checker-qual/3.41.0/checker-qual-3.41.0.jar"],
        artifact_sha256 = "2f9f245bf68e4259d610894f2406dc1f6363dc639302bd566e8272e4f4541172",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/org/checkerframework/checker-qual/3.41.0/checker-qual-3.41.0-sources.jar"],
        srcjar_sha256 = "8308220bbdd4e12b49fa06a91de685faf9cc1a376464478c80845be3e87b7d4f",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )

def org_codehaus_mojo_animal_sniffer_annotations(replacements = [], fetch_sources = True):
    maybe(
        jvm_import_external,
        name = "org_codehaus_mojo_animal_sniffer_annotations",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/org/codehaus/mojo/animal-sniffer-annotations/1.23/animal-sniffer-annotations-1.23.jar"],
        artifact_sha256 = "9ffe526bf43a6348e9d8b33b9cd6f580a7f5eed0cf055913007eda263de974d0",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/org/codehaus/mojo/animal-sniffer-annotations/1.23/animal-sniffer-annotations-1.23-sources.jar"],
        srcjar_sha256 = "4878fcc6808dbc88085a4622db670e703867754bc4bc40312c52bf3a3510d019",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
    )

    maybe(
        jvm_import_external,
        name = "org_codehaus_mojo_animal_sniffer_annotations_neverlink",
        rule_name = "java_import",
        artifact_urls = ["https://repo.maven.apache.org/maven2/org/codehaus/mojo/animal-sniffer-annotations/1.23/animal-sniffer-annotations-1.23.jar"],
        artifact_sha256 = "9ffe526bf43a6348e9d8b33b9cd6f580a7f5eed0cf055913007eda263de974d0",
        srcjar_urls = fetch_sources and ["https://repo.maven.apache.org/maven2/org/codehaus/mojo/animal-sniffer-annotations/1.23/animal-sniffer-annotations-1.23-sources.jar"],
        srcjar_sha256 = "4878fcc6808dbc88085a4622db670e703867754bc4bc40312c52bf3a3510d019",
        deps = _replace_dependencies([], replacements),
        runtime_deps = _replace_dependencies([], replacements),
        neverlink = True,
    )
