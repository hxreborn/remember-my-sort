tasks.register("clean", Delete::class) {
    group = "build"
    description = "Deletes the build directory."
    delete(
        rootProject.layout.buildDirectory
            .get()
            .asFile,
    )
}

tasks.register("assembleDebugRelease") {
    group = "build"
    description = "Assembles both debug and release builds of the app module."
    dependsOn(":app:assembleDebug", ":app:assembleRelease")
}

tasks.register("cleanBuild") {
    group = "build"
    description = "Cleans the project and then assembles all builds in the app module."
    dependsOn("clean", "assembleDebugRelease")
}

tasks.register<Exec>("buildLibxposed") {
    group = "libxposed"
    description = "Builds libxposed/api and publishes to mavenLocal"
    workingDir = file("libxposed/api")
    commandLine(
        "./gradlew",
        ":api:publishApiPublicationToMavenLocal",
        "-x",
        ":checks:compileKotlin",
        "--no-daemon"
    )
}
