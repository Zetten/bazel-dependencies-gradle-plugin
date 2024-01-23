val guavaVersion = "33.0.0-jre"
val jacksonVersion = "2.16.1"
val junitVersion = "5.10.1"
val truthVersion = "1.2.0"

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("org.sonarqube") version "4.4.1.3373"
}

group = "com.github.zetten"
version = "3.0.1"

description = """
Generate Bazel Java dependency rules from Gradle project configuration
"""

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:${jacksonVersion}"))

    implementation("com.google.guava:guava:${guavaVersion}")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation(platform("org.junit:junit-bom:${junitVersion}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.truth:truth:${truthVersion}")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

gradlePlugin {
    website = "https://github.com/Zetten/bazel-dependencies-gradle-plugin"
    vcsUrl = "https://github.com/Zetten/bazel-dependencies-gradle-plugin"

    plugins {
        create("bazelDependenciesPlugin") {
            id = "com.github.zetten.bazel-dependencies-plugin"
            displayName = "Generate Bazel Java dependency rules from Gradle project configuration"
            description =
                "A Gradle plugin that allows the generation of Bazel repository rules from Gradle project dependency configuration"
            tags = listOf("bazel", "dependencies", "compatibility")
            implementationClass = "com.github.zetten.bazeldeps.BazelDependenciesPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

sonar {
    properties {
        property("sonar.projectKey", "Zetten_bazel-dependencies-gradle-plugin")
        property("sonar.organization", "zetten")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}
