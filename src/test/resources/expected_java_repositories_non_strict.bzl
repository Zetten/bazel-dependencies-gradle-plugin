load("@bazel_tools//tools/build_defs/repo:java.bzl", "java_import_external")

def _replace_dependencies(dependencies, replacements):
    new_dependencies = depset()
    for dep in dependencies:
        if dep in replacements.keys():
            new_dependencies = depset(transitive = [new_dependencies, depset(direct = replacements.get(dep))])
        else:
            new_dependencies = depset(transitive = [new_dependencies, depset(direct = [dep])])
    return new_dependencies.to_list()

def java_repositories(excludes = [], replacements = {}):
    if "com_google_code_findbugs_jsr305" not in excludes:
        java_import_external(
            name = "com_google_code_findbugs_jsr305",
            licenses = ["notice"],
            jar_urls = [
                "https://jcenter.bintray.com/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
            ],
            jar_sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
            srcjar_urls = [
                "https://jcenter.bintray.com/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2-sources.jar",
            ],
            srcjar_sha256 = "1c9e85e272d0708c6a591dc74828c71603053b48cc75ae83cce56912a2aa063b",
        )

    if "com_google_errorprone_error_prone_annotations" not in excludes:
        java_import_external(
            name = "com_google_errorprone_error_prone_annotations",
            licenses = ["notice"],
            jar_urls = [
                "https://jcenter.bintray.com/com/google/errorprone/error_prone_annotations/2.1.3/error_prone_annotations-2.1.3.jar",
            ],
            jar_sha256 = "03d0329547c13da9e17c634d1049ea2ead093925e290567e1a364fd6b1fc7ff8",
            srcjar_urls = [
                "https://jcenter.bintray.com/com/google/errorprone/error_prone_annotations/2.1.3/error_prone_annotations-2.1.3-sources.jar",
            ],
            srcjar_sha256 = "f6ef2b585876b007051df3947b080e0d64cdd5a58b59bc69debeb26bfc0432d7",
        )

    if "com_google_guava_guava" not in excludes:
        java_import_external(
            name = "com_google_guava_guava",
            licenses = ["notice"],
            jar_urls = [
                "https://jcenter.bintray.com/com/google/guava/guava/26.0-jre/guava-26.0-jre.jar",
            ],
            jar_sha256 = "a0e9cabad665bc20bcd2b01f108e5fc03f756e13aea80abaadb9f407033bea2c",
            srcjar_urls = [
                "https://jcenter.bintray.com/com/google/guava/guava/26.0-jre/guava-26.0-jre-sources.jar",
            ],
            srcjar_sha256 = "a658eba55b72c320c45501045184c71da037cd52cd6056d597458a0c32504421",
            runtime_deps = _replace_dependencies([
                "@com_google_code_findbugs_jsr305",
                "@com_google_errorprone_error_prone_annotations",
                "@com_google_j2objc_j2objc_annotations",
                "@org_checkerframework_checker_qual",
                "@org_codehaus_mojo_animal_sniffer_annotations",
            ], replacements),
        )

    if "com_google_j2objc_j2objc_annotations" not in excludes:
        java_import_external(
            name = "com_google_j2objc_j2objc_annotations",
            licenses = ["notice"],
            jar_urls = [
                "https://jcenter.bintray.com/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar",
            ],
            jar_sha256 = "40ceb7157feb263949e0f503fe5f71689333a621021aa20ce0d0acee3badaa0f",
            srcjar_urls = [
                "https://jcenter.bintray.com/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1-sources.jar",
            ],
            srcjar_sha256 = "4858405565875ccbc050af3ad95809b32994796917c5b55ee59e186c82fc2502",
        )

    if "dom4j_dom4j" not in excludes:
        java_import_external(
            name = "dom4j_dom4j",
            licenses = ["none"],
            jar_urls = [
                "https://jcenter.bintray.com/dom4j/dom4j/1.6.1/dom4j-1.6.1.jar",
            ],
            jar_sha256 = "593552ffea3c5823c6602478b5002a7c525fd904a3c44f1abe4065c22edfac73",
            srcjar_urls = [
                "https://jcenter.bintray.com/dom4j/dom4j/1.6.1/dom4j-1.6.1-sources.jar",
            ],
            srcjar_sha256 = "4d37275f80991a37be460e73b01890172f82fd561253ba2130b62a7a5d07222d",
            runtime_deps = _replace_dependencies([
                "@xml_apis_xml_apis",
            ], replacements),
        )

    if "org_checkerframework_checker_qual" not in excludes:
        java_import_external(
            name = "org_checkerframework_checker_qual",
            licenses = ["notice"],
            jar_urls = [
                "https://jcenter.bintray.com/org/checkerframework/checker-qual/2.5.2/checker-qual-2.5.2.jar",
            ],
            jar_sha256 = "64b02691c8b9d4e7700f8ee2e742dce7ea2c6e81e662b7522c9ee3bf568c040a",
            srcjar_urls = [
                "https://jcenter.bintray.com/org/checkerframework/checker-qual/2.5.2/checker-qual-2.5.2-sources.jar",
            ],
            srcjar_sha256 = "821c5c63a6f156a3bb498c5bbb613580d9d8f4134131a5627d330fc4018669d2",
        )

    if "org_codehaus_mojo_animal_sniffer_annotations" not in excludes:
        java_import_external(
            name = "org_codehaus_mojo_animal_sniffer_annotations",
            licenses = ["notice"],
            jar_urls = [
                "https://jcenter.bintray.com/org/codehaus/mojo/animal-sniffer-annotations/1.14/animal-sniffer-annotations-1.14.jar",
            ],
            jar_sha256 = "2068320bd6bad744c3673ab048f67e30bef8f518996fa380033556600669905d",
            srcjar_urls = [
                "https://jcenter.bintray.com/org/codehaus/mojo/animal-sniffer-annotations/1.14/animal-sniffer-annotations-1.14-sources.jar",
            ],
            srcjar_sha256 = "d821ae1f706db2c1b9c88d4b7b0746b01039dac63762745ef3fe5579967dd16b",
        )

    if "xml_apis_xml_apis" not in excludes:
        java_import_external(
            name = "xml_apis_xml_apis",
            licenses = ["notice"],
            jar_urls = [
                "https://jcenter.bintray.com/xml-apis/xml-apis/1.0.b2/xml-apis-1.0.b2.jar",
            ],
            jar_sha256 = "8232f3482c346d843e5e3fb361055771c1acc105b6d8a189eb9018c55948cf9f",
            srcjar_urls = [
                "https://jcenter.bintray.com/xml-apis/xml-apis/1.0.b2/xml-apis-1.0.b2-sources.jar",
            ],
            srcjar_sha256 = "469f17d8a34cba6554769b2be7fd0727c1ca28536c7929bbc1572753452b596a",
        )
