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

rootProject.name = "NightBrief"

include(
    ":app",
    ":core-astro",
    ":core-weather",
    ":core-sites",
    ":core-gear",
    ":core-score",
    ":data",
    ":work",
)
