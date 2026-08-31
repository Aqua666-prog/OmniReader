pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required only by the TIFF decoder transitive native artifact.
        maven("https://jitpack.io")
    }
}

rootProject.name = "OmniReader"
include(":app")
