plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.openscreentime.shared"
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

// No Firebase here on purpose: this module has to compile into a local-only build, which
// contains no cloud code at all. The Firestore implementation of FamilyRepository lives in
// the :cloud module, which only the "cloud" flavor of each app depends on.
dependencies {
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)

    testImplementation(libs.junit)
}
