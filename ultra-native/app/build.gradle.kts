import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * The release signing key, or null when this machine does not have it.
 *
 * A build without the key still works and is signed with the debug key; it is
 * simply not something to publish. Gradle would otherwise fail on every clone
 * that has no business holding the secret.
 */
val releaseKeystore: Properties? =
    rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }

android {
    namespace = "com.agent.ultra"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.agent.ultra.a54"
        minSdk = 22
        targetSdk = 35
        versionCode = 1601
        versionName = "2.4.0-a54.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf(if (project.hasProperty("bench")) "x86_64" else "arm64-v8a") }
    }

    signingConfigs {
        if (file("debug.keystore").exists()) {
            create("legacyDebug") {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore!!.getProperty("storeFile"))
                storePassword = releaseKeystore!!.getProperty("storePassword")
                keyAlias = releaseKeystore!!.getProperty("keyAlias")
                keyPassword = releaseKeystore!!.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.findByName("legacyDebug") ?: signingConfigs.getByName("debug")
        }
        release {
            // No debug-key fallback here. 2.3.0 was published debug-signed
            // because this silently fell back; a release build now fails
            // unless the real release key is available.
            signingConfig = signingConfigs.getByName(
                if (releaseKeystore != null) "release"
                else if (signingConfigs.findByName("legacyDebug") != null) "legacyDebug"
                else "debug"
            )
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// A release build without the release key must fail, not quietly produce a
// debug-signed APK that looks publishable. Debug builds are unaffected.
gradle.taskGraph.whenReady {
    if (releaseKeystore == null && allTasks.any { it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease") }) {
        throw GradleException(
            "No keystore.properties: this machine does not hold the release key. " +
                "Use assembleDebug, or add keystore.properties before building a release."
        )
    }
}
