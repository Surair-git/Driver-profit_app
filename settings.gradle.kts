pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "DriverProfit"

include(":core")

// The Android module needs the Android SDK. Only include it when an SDK is
// available so the pure-JVM `core` module can be built and tested anywhere.
val sdkFromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
val localProps = File(rootDir, "local.properties")
val hasSdk = (sdkFromEnv != null && File(sdkFromEnv).isDirectory) ||
    (localProps.exists() && localProps.readText().contains("sdk.dir"))
if (hasSdk) {
    include(":app")
} else {
    logger.lifecycle("Android SDK not found: skipping :app module (set ANDROID_HOME to build the APK)")
}
