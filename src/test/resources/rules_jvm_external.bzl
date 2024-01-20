load("@rules_jvm_external//:specs.bzl", "maven")

REPOSITORIES = ["https://repo.maven.apache.org/maven2/"]

ARTIFACTS = [
    maven.artifact("com.google.android", "annotations", "4.1.1.4"),
    maven.artifact("com.google.code.findbugs", "jsr305", "3.0.2"),
    maven.artifact("com.google.code.gson", "gson", "2.10.1"),
    maven.artifact("com.google.errorprone", "error_prone_annotations", "2.23.0"),
    maven.artifact("com.google.guava", "failureaccess", "1.0.2"),
    maven.artifact("com.google.guava", "guava", "33.0.0-jre"),
    maven.artifact("com.google.guava", "listenablefuture", "9999.0-empty-to-avoid-conflict-with-guava"),
    maven.artifact("com.google.j2objc", "j2objc-annotations", "2.8"),
    maven.artifact("io.grpc", "grpc-api", "1.61.0"),
    maven.artifact("io.grpc", "grpc-context", "1.61.0"),
    maven.artifact("io.grpc", "grpc-core", "1.61.0", exclusions = ["io.grpc:grpc-util"]),
    maven.artifact("io.perfmark", "perfmark-api", "0.26.0"),
    maven.artifact("org.checkerframework", "checker-qual", "3.41.0"),
    maven.artifact("org.codehaus.mojo", "animal-sniffer-annotations", "1.23"),
]

EXCLUSIONS = []
