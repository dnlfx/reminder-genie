import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes these in. Locally the app builds as version 1 and release builds use the debug key.
val versionCodeFromCi = System.getenv("RG_VERSION_CODE")?.toIntOrNull() ?: 1
val keystorePath: String? = System.getenv("RG_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.dnlfx.remindergenie"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dnlfx.remindergenie"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeFromCi
        versionName = "1.0.$versionCodeFromCi"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("sideload") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RG_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "reminder-genie"
                keyPassword = System.getenv("RG_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: System.getenv("RG_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Every APK must be signed. Without the sideload key (a CI secret) this falls back to the
            // debug key, which installs fine but can't update an install signed with the real key.
            signingConfig = signingConfigs.findByName("sideload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        animationsDisabled = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
