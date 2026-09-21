plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

// The Firebase half of OpenScreenTime: the FamilyRepository implementation that pairs two phones
// and syncs a child's profile and stats between them. Only the "cloud" product flavor of each app
// depends on this module, so a "local" build contains none of it - no Firebase SDK, no
// google-services.json, no network code at all (see each app's build.gradle.kts).
//
// The google-services plugin lives here rather than in the apps on purpose: it fails the build for
// any variant that has no google-services.json, which is exactly what a local build is.
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
    api(libs.firebase.crashlytics)
    implementation(libs.coroutines.play.services)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
