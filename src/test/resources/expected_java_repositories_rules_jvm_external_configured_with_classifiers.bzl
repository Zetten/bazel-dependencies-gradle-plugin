load("@rules_jvm_external//:specs.bzl", "maven")

REPOSITORIES = [
    "https://repo.maven.apache.org/maven2/",
]

ARTIFACTS = [
    maven.artifact("com.google.code.findbugs", "jsr305", "3.0.2"),
    maven.artifact("com.google.errorprone", "error_prone_annotations", "2.1.3", neverlink = True),
    maven.artifact("com.google.guava", "guava", "26.0-jre"),
    maven.artifact("com.google.j2objc", "j2objc-annotations", "1.1", neverlink = True),
    maven.artifact("dom4j", "dom4j", "1.6.1"),
    maven.artifact("io.netty", "netty-buffer", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-codec", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-codec-dns", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-codec-http", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-codec-socks", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-common", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-handler", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-handler-proxy", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-resolver", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-resolver-dns", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-resolver-dns-classes-macos", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-resolver-dns-native-macos", "4.1.90.Final", classifier = "osx-x86_64"),
    maven.artifact("io.netty", "netty-transport", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-transport-classes-epoll", "4.1.90.Final"),
    maven.artifact("io.netty", "netty-transport-native-epoll", "4.1.90.Final", classifier = "linux-x86_64"),
    maven.artifact("io.netty", "netty-transport-native-unix-common", "4.1.90.Final"),
    maven.artifact("io.projectreactor.netty", "reactor-netty-core", "1.1.5"),
    maven.artifact("io.projectreactor", "reactor-core", "3.5.4"),
    maven.artifact("junit", "junit", "4.12", testonly = True),
    maven.artifact("org.checkerframework", "checker-qual", "2.5.2"),
    maven.artifact("org.codehaus.mojo", "animal-sniffer-annotations", "1.14", neverlink = True),
    maven.artifact("org.hamcrest", "hamcrest-core", "1.3"),
    maven.artifact("org.reactivestreams", "reactive-streams", "1.0.4"),
    maven.artifact("xml-apis", "xml-apis", "1.0.b2"),
]