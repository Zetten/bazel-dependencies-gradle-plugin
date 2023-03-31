import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.15.0"
}

group = "com.github.zetten"
version = "2.3.0"

description = """
Generate Bazel Java dependency rules from Gradle project configuration
"""

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.github.jk1:gradle-license-report:2.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.5")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.13.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.5")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("com.google.truth:truth:1.1.3")
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

tasks.test {
    useJUnitPlatform()
}