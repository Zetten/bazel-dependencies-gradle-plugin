load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

def _replace_dependencies(dependencies, replacements):
    new_dependencies = depset()
    for dep in dependencies:
        if dep in replacements.keys():
            new_dependencies = depset(transitive = [new_dependencies, depset(direct = replacements.get(dep))])
        else:
            new_dependencies = depset(transitive = [new_dependencies, depset(direct = [dep])])
    return new_dependencies.to_list()

def java_repositories(
        omit_com_google_code_findbugs_jsr305 = False,
        omit_com_google_errorprone_error_prone_annotations = False,
        omit_com_google_guava_guava = False,
        omit_com_google_j2objc_j2objc_annotations = False,
        omit_dom4j_dom4j = False,
        omit_org_checkerframework_checker_qual = False,
        omit_org_codehaus_mojo_animal_sniffer_annotations = False,
        omit_xml_apis_xml_apis = False,
        fetch_sources = False,
        replacements = {}):
    if not omit_com_google_code_findbugs_jsr305:
        com_google_code_findbugs_jsr305(fetch_sources, replacements)
    if not omit_com_google_errorprone_error_prone_annotations:
        com_google_errorprone_error_prone_annotations(fetch_sources, replacements)
    if not omit_com_google_guava_guava:
        com_google_guava_guava(fetch_sources, replacements)
    if not omit_com_google_j2objc_j2objc_annotations:
        com_google_j2objc_j2objc_annotations(fetch_sources, replacements)
    if not omit_dom4j_dom4j:
        dom4j_dom4j(fetch_sources, replacements)
    if not omit_org_checkerframework_checker_qual:
        org_checkerframework_checker_qual(fetch_sources, replacements)
    if not omit_org_codehaus_mojo_animal_sniffer_annotations:
        org_codehaus_mojo_animal_sniffer_annotations(fetch_sources, replacements)
    if not omit_xml_apis_xml_apis:
        xml_apis_xml_apis(fetch_sources, replacements)

def com_google_code_findbugs_jsr305(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "com_google_code_findbugs_jsr305",
        artifact = "com.google.code.findbugs:jsr305:3.0.2",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = None,
        runtime_deps = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=com.google.code.findbugs:jsr305:3.0.2",
        ],
    )

def com_google_errorprone_error_prone_annotations(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "com_google_errorprone_error_prone_annotations",
        artifact = "com.google.errorprone:error_prone_annotations:2.1.3",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "03d0329547c13da9e17c634d1049ea2ead093925e290567e1a364fd6b1fc7ff8",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = None,
        runtime_deps = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=com.google.errorprone:error_prone_annotations:2.1.3",
        ],
    )

def com_google_guava_guava(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "com_google_guava_guava",
        artifact = "com.google.guava:guava:26.0-jre",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "a0e9cabad665bc20bcd2b01f108e5fc03f756e13aea80abaadb9f407033bea2c",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = None,
        runtime_deps = _replace_dependencies([
            "@com_google_code_findbugs_jsr305",
            "@org_checkerframework_checker_qual",
            "@com_google_errorprone_error_prone_annotations",
            "@com_google_j2objc_j2objc_annotations",
            "@org_codehaus_mojo_animal_sniffer_annotations",
        ], replacements),
        tags = [
            "maven_coordinates=com.google.guava:guava:26.0-jre",
        ],
    )

def com_google_j2objc_j2objc_annotations(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "com_google_j2objc_j2objc_annotations",
        artifact = "com.google.j2objc:j2objc-annotations:1.1",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "2994a7eb78f2710bd3d3bfb639b2c94e219cedac0d4d084d516e78c16dddecf6",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = None,
        runtime_deps = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=com.google.j2objc:j2objc-annotations:1.1",
        ],
    )

def dom4j_dom4j(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "dom4j_dom4j",
        artifact = "dom4j:dom4j:1.6.1",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "593552ffea3c5823c6602478b5002a7c525fd904a3c44f1abe4065c22edfac73",
        licenses = ["none"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = None,
        runtime_deps = _replace_dependencies([
            "@xml_apis_xml_apis",
        ], replacements),
        tags = [
            "maven_coordinates=dom4j:dom4j:1.6.1",
        ],
    )

def org_checkerframework_checker_qual(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "org_checkerframework_checker_qual",
        artifact = "org.checkerframework:checker-qual:2.5.2",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "64b02691c8b9d4e7700f8ee2e742dce7ea2c6e81e662b7522c9ee3bf568c040a",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = None,
        runtime_deps = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=org.checkerframework:checker-qual:2.5.2",
        ],
    )

def org_codehaus_mojo_animal_sniffer_annotations(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "org_codehaus_mojo_animal_sniffer_annotations",
        artifact = "org.codehaus.mojo:animal-sniffer-annotations:1.14",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "2068320bd6bad744c3673ab048f67e30bef8f518996fa380033556600669905d",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = None,
        runtime_deps = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=org.codehaus.mojo:animal-sniffer-annotations:1.14",
        ],
    )

def xml_apis_xml_apis(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "xml_apis_xml_apis",
        artifact = "xml-apis:xml-apis:1.0.b2",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "8232f3482c346d843e5e3fb361055771c1acc105b6d8a189eb9018c55948cf9f",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = None,
        runtime_deps = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=xml-apis:xml-apis:1.0.b2",
        ],
    )
