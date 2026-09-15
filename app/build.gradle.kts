import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ── Release signing ──────────────────────────────────────────────────────────
// A `keystore.properties` file at the repo root (git-ignored) supplies the project's own signing
// key. Without it we fall back to the DEBUG keystore, which is what this project has always been
// distributed with (every `releases/*.apk` to date is debug-signed). That fallback matters twice
// over: it keeps `assembleRelease` producing an *installable* APK — it used to emit an unsigned
// one that Android refuses — and it keeps in-place updates working, because switching to a new key
// makes Android refuse to install over an existing debug-signed install.
//
// Create one with (and then keep the .jks safe — losing it means no future in-place updates):
//   keytool -genkeypair -v -keystore release.jks -alias timetable \
//     -keyalg RSA -keysize 2048 -validity 10000
// and write keystore.properties:
//   storeFile=release.jks
//   storePassword=…
//   keyAlias=timetable
//   keyPassword=…
val keystorePropsFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropsFile.exists()
val keystoreProps = Properties().apply {
    if (hasReleaseKeystore) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.example.timetablescraper"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.timetablescraper"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Signed, always: with the project key when one is configured, otherwise with the
            // debug key so the APK stays installable (and updateable in place). See above.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // R8 + resource shrinking. Room and WorkManager ship their own consumer rules for the
            // classes they look up by name; proguard-rules.pro adds what they do not cover.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- Networking & Parsing ---

    // Networking: OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines for background tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --- Local caching: Room ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Background sync: WorkManager ---
    implementation(libs.androidx.work.runtime.ktx)
}

// ── Test filtering via -PtestFilter ────────────────────────────────
// Usage:  ./gradlew test -PtestFilter="com.example.timetablescraper.api.*"
//         ./gradlew test                            # run all tests
tasks.withType<Test>().configureEach {
    if (project.hasProperty("testFilter")) {
        filter {
            includeTestsMatching(project.property("testFilter") as String)
        }
    }
}