pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal() // ✅ 로컬 저장소 추가
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()  // ✅ 반드시 추가!
    }
}

rootProject.name = "GrowSpaceTestApp"
include(":app")

