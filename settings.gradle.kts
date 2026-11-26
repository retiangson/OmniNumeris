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
    }
}

rootProject.name = "OmniNumeris"
include(":app", ":application", ":domain", ":infrastructure")

if (providers.gradleProperty("inferenceSpike").orNull == "true") {
    include(":inference-spike")
}
