// Todos los plugins se cargan en el classpath raíz para que compartan classloader
// (el plugin de Kotlin para Android necesita ver las clases del plugin de Android).
// El plugin de Android solo se agrega cuando hay SDK, así `:core:test` corre en cualquier lado.
buildscript {
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
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.0.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
        val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        val props = File(rootDir, "local.properties")
        val withAndroid = (sdk != null && File(sdk).isDirectory) || (props.exists() && props.readText().contains("sdk.dir"))
        if (withAndroid) classpath("com.android.tools.build:gradle:8.7.3")
    }
}
