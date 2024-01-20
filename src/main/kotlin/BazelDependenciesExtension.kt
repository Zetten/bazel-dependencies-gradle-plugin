package com.github.zetten.bazeldeps

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import java.io.File
import javax.inject.Inject

open class BazelDependenciesExtension @Inject constructor(project: Project) {
    val configuration: Property<Configuration> = project.objects.property()
    val outputFile: RegularFileProperty = project.objects.fileProperty()
    val neverlink: SetProperty<String> = project.objects.setProperty()
    val testonly: SetProperty<String> = project.objects.setProperty()
    val jvmImportExternal: JvmImportExternalExtension =
        project.objects.newInstance(JvmImportExternalExtension::class.java)
    val rulesJvmExternal: RulesJvmExternalExtension =
        project.objects.newInstance(RulesJvmExternalExtension::class.java)

    fun jvmImportExternal(action: Action<JvmImportExternalExtension>) {
        action.execute(jvmImportExternal)
    }

    fun rulesJvmExternal(action: Action<RulesJvmExternalExtension>) {
        action.execute(rulesJvmExternal)
    }
}

open class JvmImportExternalExtension @Inject constructor(project: Project) {
    val createAggregatorRepo: Property<Boolean> = project.objects.property<Boolean>().convention(true)
}

open class RulesJvmExternalExtension @Inject constructor(project: Project) {
    val version: Property<String> = project.objects.property<String>().convention("6.0")
    val createMavenInstallJson: Property<Boolean> = project.objects.property<Boolean>().convention(true)
}