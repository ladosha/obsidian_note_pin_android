plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vladmas.notepinobsidian"
    // Current AndroidX releases require compiling against 37; runtime behaviour follows targetSdk.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vladmas.notepinobsidian"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            // Leaves the git commit out of the APK, so a build from the release tag matches the published APK
            // byte for byte, which F-Droid checks before it publishes the developer-signed APK.
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Lists the app's languages (from the values-* folders) for Android 13+'s per-app language setting.
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    // The real org.json; Android's copy is a stub in local unit tests.
    testImplementation(libs.json)
}
