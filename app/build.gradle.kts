plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.izumii.dcandroid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.izumii.dcandroid"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        manifestPlaceholders["appName"] = "DC"
        manifestPlaceholders["appIcon"] = "@drawable/ic_discord_logo"
        manifestPlaceholders["roundIcon"] = "@drawable/ic_discord_logo"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = "DC.apk"
        }
    }

    signingConfigs {
        create("release") {
            val keystore = file("signing/release.keystore")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = "bc_release_key"
                keyAlias = "bc_release"
                keyPassword = "bc_release_key"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
