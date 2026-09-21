plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// The Firebase half of OpenScreenTime: the FamilyRepository implementation that pairs two phones
// and syncs a child's profile and stats between them. Only the "cloud" product flavor of each app
// depends on this module, so a "local" build contains none of it - no Firebase SDK, no
// google-services.json, no network code at all (see each app's build.gradle.kts).
//
// Firebase is started by hand from cloud/src/main/assets/google-services.json (see
// FirebaseBootstrap). The google-services Gradle plugin is deliberately not used: it only generates
// its config resources for application modules, so applied here it silently produced nothing and a
// cloud build crashed on launch - and applied to the apps it fails any variant without a json,
// which is exactly what a local build is.
android {
    namespace = "org.openscreentime.cloud"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":shared"))

    // api, not implementation: each app's cloud-flavor Backend.kt wires Firebase up directly
    // (emulator host, Crashlytics collection), so these have to reach its compile classpath.
    api(platform(libs.firebase.bom))
    api(libs.firebase.firestore)
    api(libs.firebase.auth)
    implementation(libs.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
