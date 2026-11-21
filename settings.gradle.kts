rootProject.name = "dawnshell"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement @Suppress("UnstableApiUsage") {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://jitpack.io") { name = "Jitpack" }    // libsuperuser
    }
}

include(":app")
