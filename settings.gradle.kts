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
        // youtubedl-android (motor yt-dlp) é distribuído via JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "PRISMA Baixador"
include(":app")
