plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val configuredVersionCode = providers.gradleProperty("versionCode")
    .orNull
    ?.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: 1
val configuredVersionName = providers.gradleProperty("versionName")
    .orElse("0.1.0")
    .get()

android {
    namespace = "com.sousoulab.einklauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sousoulab.einklauncher"
        minSdk = 21
        targetSdk = 33
        versionCode = configuredVersionCode
        versionName = configuredVersionName

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        buildConfig = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // API 33 is an intentional product requirement for direct APK distribution.
        disable += "OldTargetApi"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0") {
        // FileProvider is needed, but profileinstaller would add delayed startup work.
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
    }
    testImplementation("junit:junit:4.13.2")
}
