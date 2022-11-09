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
        omit_com_google_guava_failureaccess = False,
        omit_com_google_guava_guava = False,
        omit_com_google_guava_listenablefuture = False,
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
    if not omit_com_google_guava_failureaccess:
        com_google_guava_failureaccess(fetch_sources, replacements)
    if not omit_com_google_guava_guava:
        com_google_guava_guava(fetch_sources, replacements)
    if not omit_com_google_guava_listenablefuture:
        com_google_guava_listenablefuture(fetch_sources, replacements)
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
        licenses = ["restricted"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = "1c9e85e272d0708c6a591dc74828c71603053b48cc75ae83cce56912a2aa063b",
        exports = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=com.google.code.findbugs:jsr305:3.0.2",
        ],
    )

def com_google_errorprone_error_prone_annotations(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "com_google_errorprone_error_prone_annotations",
        artifact = "com.google.errorprone:error_prone_annotations:2.2.0",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "6ebd22ca1b9d8ec06d41de8d64e0596981d9607b42035f9ed374f9de271a481a",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = "626adccd4894bee72c3f9a0384812240dcc1282fb37a87a3f6cb94924a089496",
        exports = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=com.google.errorprone:error_prone_annotations:2.2.0",
        ],
    )

def com_google_guava_failureaccess(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "com_google_guava_failureaccess",
        artifact = "com.google.guava:failureaccess:1.0.1",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = "092346eebbb1657b51aa7485a246bf602bb464cc0b0e2e1c7e7201fadce1e98f",
        exports = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=com.google.guava:failureaccess:1.0.1",
        ],
    )

def com_google_guava_guava(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "com_google_guava_guava",
        artifact = "com.google.guava:guava:27.1-jre",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "4a5aa70cc968a4d137e599ad37553e5cfeed2265e8c193476d7119036c536fe7",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = "9de05c573971cedfcd53fb85fc7a58a5f453053026a9bf18594cffc79a1d6874",
        exports = _replace_dependencies([
            "@com_google_guava_failureaccess",
            "@com_google_guava_listenablefuture",
            "@com_google_code_findbugs_jsr305",
            "@org_checkerframework_checker_qual",
            "@com_google_errorprone_error_prone_annotations",
            "@com_google_j2objc_j2objc_annotations",
            "@org_codehaus_mojo_animal_sniffer_annotations",
        ], replacements),
        tags = [
            "maven_coordinates=com.google.guava:guava:27.1-jre",
        ],
    )

def com_google_guava_listenablefuture(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "com_google_guava_listenablefuture",
        artifact = "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99",
        licenses = ["notice"],
        fetch_sources = False,
        srcjar_sha256 = None,
        exports = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
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
        srcjar_sha256 = "2cd9022a77151d0b574887635cdfcdf3b78155b602abc89d7f8e62aba55cfb4f",
        exports = _replace_dependencies([
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
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = "4d37275f80991a37be460e73b01890172f82fd561253ba2130b62a7a5d07222d",
        exports = _replace_dependencies([
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
        srcjar_sha256 = "821c5c63a6f156a3bb498c5bbb613580d9d8f4134131a5627d330fc4018669d2",
        exports = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=org.checkerframework:checker-qual:2.5.2",
        ],
    )

def org_codehaus_mojo_animal_sniffer_annotations(fetch_sources, replacements):
    jvm_maven_import_external(
        name = "org_codehaus_mojo_animal_sniffer_annotations",
        artifact = "org.codehaus.mojo:animal-sniffer-annotations:1.17",
        server_urls = [
            "https://repo.maven.apache.org/maven2/",
        ],
        artifact_sha256 = "92654f493ecfec52082e76354f0ebf87648dc3d5cec2e3c3cdb947c016747a53",
        licenses = ["notice"],
        fetch_sources = fetch_sources,
        srcjar_sha256 = "2571474a676f775a8cdd15fb9b1da20c4c121ed7f42a5d93fca0e7b6e2015b40",
        exports = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=org.codehaus.mojo:animal-sniffer-annotations:1.17",
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
        srcjar_sha256 = "469f17d8a34cba6554769b2be7fd0727c1ca28536c7929bbc1572753452b596a",
        exports = _replace_dependencies([
        ], replacements),
        tags = [
            "maven_coordinates=xml-apis:xml-apis:1.0.b2",
        ],
    )
