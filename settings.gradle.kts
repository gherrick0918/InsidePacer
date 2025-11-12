pluginManagement { 
    repositories { 
        maven { url = uri("https://dl.google.com/dl/android/maven2/") }
        google()
        gradlePluginPortal()
        mavenCentral() 
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application", 
                "com.android.library" -> {
                    useModule("com.android.tools.build:gradle:${requested.version}")
                }
            }
        }
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