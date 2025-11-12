pluginManagement { 
    repositories { 
        gradlePluginPortal()
        maven { url = uri("https://maven.google.com") }
        mavenCentral() 
    } 
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { 
        maven { url = uri("https://maven.google.com") }
        mavenCentral() 
    }
}

rootProject.name = "InsidePacer"
include(":app")
include(":healthconnect")