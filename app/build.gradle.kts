import java.io.File
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val documentSdkSpike = providers.gradleProperty("documentSdkSpike").orNull == "true"
val mathOcrSpike = providers.gradleProperty("mathOcrSpike").orNull == "true"
val formulaAndroidSpike = providers.gradleProperty("formulaAndroidSpike").orNull == "true"
val notebookDeviceFixture = providers.gradleProperty("notebookDeviceFixture").orNull == "true"

if (notebookDeviceFixture) layout.buildDirectory.set(layout.projectDirectory.dir("build/notebook-device-fixture"))

fun verifiedBenchmarkAar(relativePath: String, expectedSha256: String): File {
    val aar = rootProject.file(relativePath)
    require(aar.isFile) { "Stage the benchmark AAR at $relativePath" }
    val digest = MessageDigest.getInstance("SHA-256")
    aar.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        var count = input.read(buffer)
        while (count != -1) {
            digest.update(buffer, 0, count)
            count = input.read(buffer)
        }
    }
    require(digest.digest().joinToString("") { "%02x".format(it) } == expectedSha256) {
        "Benchmark AAR SHA-256 mismatch at $relativePath"
    }
    return aar
}

android {
    namespace = "com.omninumeris.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.omninumeris.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (notebookDeviceFixture) applicationIdSuffix = ".notebookfixture"
            ndk.abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
        release {
            ndk.abiFilters.add("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = documentSdkSpike
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    if (documentSdkSpike) {
        sourceSets.getByName("androidTest") {
            java.srcDir("src/documentSdkTest/kotlin")
            assets.srcDir("src/documentSdkTest/assets")
        }
    }
    if (mathOcrSpike) {
        sourceSets.getByName("androidTest") {
            java.srcDir("src/mathOcrTest/kotlin")
            assets.srcDir(rootProject.file(".gradle-local/math-ocr/tessdata"))
        }
    }
    if (mathOcrSpike || formulaAndroidSpike) {
        sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("docs/benchmarks/math-ocr-corpus"))
    }
    if (formulaAndroidSpike) {
        sourceSets.getByName("androidTest") {
            java.srcDir("src/formulaAndroidTest/kotlin")
            assets.srcDir(rootProject.file(".gradle-local/kan30-pix2text/assets"))
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.junit)
    if (mathOcrSpike) {
        androidTestImplementation(files(verifiedBenchmarkAar(
            ".gradle-local/kan30-tesseract-android/tesseract4android-4.9.0.aar",
            "bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a",
        )))
    }
    if (formulaAndroidSpike) {
        androidTestImplementation(files(verifiedBenchmarkAar(
            ".gradle-local/kan30-pix2text/onnxruntime-android-1.24.1.aar",
            "133134e2b01ffb91f8f66d46a3149f7fe402ac2e3e2d1df4f8fc679e999689f6",
        )))
    }
    if (documentSdkSpike) {
        coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
        androidTestImplementation("com.tom-roush:pdfbox-android:2.0.27.0")
        androidTestImplementation("org.readium.kotlin-toolkit:readium-streamer:3.1.2")
    }
}
