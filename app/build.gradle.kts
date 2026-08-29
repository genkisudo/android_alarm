plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Required from Kotlin 2.0 onward: the Compose compiler was unbundled from Kotlin's
    // tooling and now needs this plugin applied explicitly (composeOptions.kotlinCompiler-
    // ExtensionVersion no longer does anything). Without it, buildFeatures.compose = true
    // fails at build configuration time.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.silentalarm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.silentalarm.app"
        // minSdk 33: USE_EXACT_ALARM is install-time for alarm-clock apps at this API level,
        // so there is no runtime exact-alarm settings screen (SPEC.md Assumption A1).
        // Target device is a Redmi Note 11 Pro on Android 13 (MIUI 14) - see SPEC.md.
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":scheduling"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
