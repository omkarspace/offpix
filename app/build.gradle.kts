plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.offpix.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.offpix.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        ndk {
            // Focus on 64-bit ARM devices (required by Google Play) + x86_64 for emulators
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("OFFPIX_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("OFFPIX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OFFPIX_KEY_ALIAS")
                keyPassword = System.getenv("OFFPIX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val ksPath = System.getenv("OFFPIX_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // litert-api:2.3.0 ships with Kotlin 2.4.0 metadata
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Install-time Play Asset Delivery pack (~290-330 MB total models)
    assetPacks += setOf(":pack_models")

    androidResources {
        // Keep .tflite models uncompressed so LiteRT can memory-map directly without RAM copies
        noCompress += listOf("tflite", "litertlm")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Avoid known AGP 8.7 + lifecycle-2.8 NonNullableMutableLiveDataDetector lint crash on release builds
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)

    // LiteRT CompiledModel (NPU -> GPU ML Drift -> CPU XNNPACK)
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
