plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// AGP's asset merger gunzips every file whose name ends in `.gz` and drops that
// suffix (MergedAssetWriter / AssetItem.shouldBeUnGzipped). That would expand
// these grids back to ~110 MB and make GZIPInputStream fail. The repo keeps
// `src/main/assets/bortle_*.nblp.gz` (ignored by the merger). A copy task publishes
// the same bytes as `bortle_*.nblp.gzip`, which is copied through unchanged.
// AssetManager.open then returns the gzip bytes.
val bortleIgnore =
    "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:!*.gz"

android {
    namespace = "app.nightbrief.data"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    androidResources {
        noCompress += listOf("gz", "gzip")
        ignoreAssetsPattern = bortleIgnore
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

abstract class CopyBortleGrids : DefaultTask() {
    @get:InputFiles
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copyGrids() {
        val out = outputDir.get().asFile
        out.mkdirs()
        for (name in listOf("bortle_na.nblp.gz", "bortle_world.nblp.gz")) {
            val packed = out.resolve(name.removeSuffix(".gz") + ".gzip")
            source.get().asFile.resolve(name).copyTo(packed, overwrite = true)
        }
    }
}

val copyBortleGrids = tasks.register<CopyBortleGrids>("copyBortleGrids") {
    source.set(layout.projectDirectory.dir("src/main/assets"))
    outputDir.set(layout.buildDirectory.dir("generated/bortleAssets"))
}

android.sourceSets.named("main") {
    assets.srcDir(layout.buildDirectory.dir("generated/bortleAssets"))
}

tasks.configureEach {
    if (name.contains("Assets", ignoreCase = true) && name != "copyBortleGrids") {
        dependsOn(copyBortleGrids)
    }
}

dependencies {
    api(project(":core-score"))
    api(libs.androidx.datastore)
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
