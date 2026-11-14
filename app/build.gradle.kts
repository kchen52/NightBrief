import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

fun gitShortSha(): String {
    val fromCi = System.getenv("GITHUB_SHA")?.take(7)?.lowercase()
    if (!fromCi.isNullOrBlank()) return fromCi
    return try {
        val proc = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() == 0 && out.isNotBlank()) out else "local"
    } catch (_: Exception) {
        "local"
    }
}

val gitSha: String = gitShortSha()

val updateChannel: String =
    providers.gradleProperty("nightbrief.updateChannel").orElse("develop").get()

val sideloadStore = rootProject.file("ci/sideload.jks")
val releasePropsFile = rootProject.file("keystore.properties")
val releaseProps = Properties().apply {
    if (releasePropsFile.isFile) releasePropsFile.inputStream().use(::load)
}

android {
    namespace = "app.nightbrief.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.nightbrief"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("nightbrief.versionCode").get().toInt()
        versionName = providers.gradleProperty("nightbrief.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "UPDATE_CHANNEL", "\"$updateChannel\"")
        buildConfigField("String", "GITHUB_OWNER", "\"kchen52\"")
        buildConfigField("String", "GITHUB_REPO", "\"NightBrief\"")
    }

    signingConfigs {
        // Same key for local debug and CI develop APKs so the in-app updater can replace either.
        // Not the Play Store key. See README (Sideload updates).
        create("sideload") {
            storeFile = sideloadStore
            storePassword = "nightbrief-sideload"
            keyAlias = "sideload"
            keyPassword = "nightbrief-sideload"
        }
        if (releaseProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (sideloadStore.isFile) {
                signingConfig = signingConfigs.getByName("sideload")
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                releaseProps.isNotEmpty() -> signingConfigs.getByName("release")
                sideloadStore.isFile -> signingConfigs.getByName("sideload")
                else -> signingConfigs.getByName("debug")
            }
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
    androidResources {
        // Library noCompress is not enough: aapt2 stores the Bortle `.gzip` grids only if the app says so.
        noCompress += "gzip"
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

// Compose UI tests need the debug-only ComponentActivity from ui-test-manifest.
// Release unit tests do not get that manifest entry, so they cannot host createComposeRule.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach {
    enabled = false
}

// Paparazzi keeps layoutlib in the test process. After a Compose test has shown the site form,
// a later Compose test in that same process never goes idle.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    forkEvery = 1
}

dependencies {
    implementation(project(":work"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.haze)
    implementation(libs.haze.glass)
    implementation(libs.haze.blur)
    implementation(libs.osmdroid)
    implementation(libs.androidx.window)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.play.services.wearable)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestUtil(libs.androidx.test.orchestrator)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
