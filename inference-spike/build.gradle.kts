plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val llamaArchive = providers.gradleProperty("llamaArchive")

android {
    namespace = "com.omninumeris.inferencespike"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.omninumeris.inferencespike"
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk.abiFilters.add("arm64-v8a")
        externalNativeBuild.cmake {
            arguments.add("-DANDROID_STL=c++_static")
            llamaArchive.orNull?.let { archive ->
                arguments.add("-DLLAMA_ARCHIVE=${rootProject.file(archive).invariantSeparatorsPath}")
            }
        }
    }

    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents.beforeVariants(androidComponents.selector().withBuildType("release")) {
    it.enable = false
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.junit)
}
