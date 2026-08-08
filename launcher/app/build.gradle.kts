plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val signingKeystore = rootProject.file("keystore.jks")
val hasSigningKeystore = signingKeystore.exists()

android {
    namespace = "com.nubia.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nubia.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasSigningKeystore) {
            create("stable") {
                storeFile = signingKeystore
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "Launcher2026!"
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "launcher"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "Launcher2026!"
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigningKeystore) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
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
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
