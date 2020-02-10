plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.10.1"
    kotlin("plugin.serialization") version "1.3.61"
}

group = "com.github.zetten"
version = "1.7.0"

description = """
Generate Bazel Java dependency rules from Gradle project configuration
"""

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("gradle.plugin.com.github.jk1:gradle-license-report:1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.14.0")
    implementation("io.projectreactor:reactor-core:3.3.2.RELEASE")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.0.2.RELEASE")
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
