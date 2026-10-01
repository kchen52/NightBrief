plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.nightbrief.data"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    androidResources {
        // `.gzip` is not the suffix AGP's asset merger unpacks (that is `.gz`).
        // noCompress keeps aapt from deflating those bytes inside the APK.
        noCompress += "gzip"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    api(project(":core-score"))
    api(libs.androidx.datastore)
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
