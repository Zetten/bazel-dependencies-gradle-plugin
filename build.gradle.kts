plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.10.0"
}

group = "com.github.zetten"
version = "1.1.0"

description = """
Generate Bazel Java dependency rules from Gradle project configuration
"""

repositories {
    gradlePluginPortal()
}

dependencies {
    compile("gradle.plugin.com.github.jk1:gradle-license-report:1.3")
    testImplementation("junit:junit:4.12")
}

pluginBundle {
    // please change these URLs to point to your own website/repository
    website = "https://github.com/Zetten/bazel-dependencies-gradle-plugin"
    vcsUrl = "https://github.com/Zetten/bazel-dependencies-gradle-plugin"
    tags = listOf("bazel", "dependencies", "compatibility")
}

gradlePlugin {
    plugins {
        register("bazelDependenciesPlugin") {
            id = "com.github.zetten.bazel-dependencies-plugin"
            displayName = "Generate Bazel Java dependency rules from Gradle project configuration"
            description = "A Gradle plugin that allows the generation of Bazel repository rules from Gradle project dependency configuration"
            implementationClass = "com.github.zetten.bazeldeps.BazelDependenciesPlugin"
        }
    }
}
