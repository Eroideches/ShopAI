// Build minimo per testare solo :core su una macchina senza SDK Android:
//   gradle -p tools/core-test :core:test
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "core-test"
include(":core")
project(":core").projectDir = file("../../core")
