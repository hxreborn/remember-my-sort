import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Delete

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    base
    alias(libs.plugins.agp.app) apply false
}

tasks.named<Delete>("clean") {
    group = BasePlugin.BUILD_GROUP
    description = "Deletes the build directory."
    delete(rootProject.layout.buildDirectory)
}

tasks.register("assembleDebugRelease") {
    group = BasePlugin.BUILD_GROUP
    description = "Assembles both debug and release builds of the app module."
    dependsOn(":app:assembleDebug", ":app:assembleRelease")
}

tasks.register("cleanBuild") {
    group = BasePlugin.BUILD_GROUP
    description = "Cleans the project and then assembles all builds in the app module."
    dependsOn("clean", "assembleDebugRelease")
}
