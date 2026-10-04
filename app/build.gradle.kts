plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.novacamera"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.novacamera"
        // Android 7.0 (API 24) through current: all version-gated APIs
        // (MediaStore Q/O branches, dynamic color S, concurrent camera P)
        // already have pre-Q/O/P/S fallbacks.
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // CameraX concurrent camera + HDR capabilities are queried at runtime.
        // Keep NDK debug symbols stripped for release packaging.
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources { excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
    }

    // Two variants: gms (ML Kit thin clients via Play) and foss
    // (no Google libraries at all — F-Droid eligible). Flavor-specific
    // code lives in src/gms + src/foss with identical class names.
    flavorDimensions += "services"
    productFlavors {
        create("gms") { dimension = "services" }
        create("foss") { dimension = "services" }
    }

    // Per-ABI APKs: native libs ship 4x (arm64/armv7/x86/x86_64) in a
    // universal APK. Splits cut each APK to ~1/4 of the fat size.
    // For Play uploads prefer bundleRelease (AAB); splits are for sideload.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    implementation(libs.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.prefs)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // CameraX engine
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)

    // ML Kit Play thin clients — gms flavor only (models via Play, not bundled).
    // The foss flavor excludes all Google libraries (see src/foss stubs).
    // NOTE: string form (not gmsImplementation accessor) for script compatibility.
    add("gmsImplementation", libs.mlkit.face)
    add("gmsImplementation", libs.mlkit.barcode)
    add("gmsImplementation", libs.mlkit.text)

    // Storage / security / UI
    implementation(libs.exifinterface)
    implementation(libs.biometric)
    implementation(libs.security.crypto)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    testImplementation(libs.junit)
}
