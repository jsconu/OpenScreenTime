import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Local, gitignored developer settings (see local.properties, already gitignored for the
// Android-Studio-managed sdk.dir) - keeps the feedback destination email out of this public,
// MIT-licensed repo's git history entirely. Falls back to a clearly-fake placeholder so CI
// and other contributors can still build without it; the feedback button just wouldn't
// address anywhere useful until this is set. See #21 and the README for the one-line setup.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "org.openscreentime.parent"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.openscreentime.parent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Opt-in flag for the E2E workflow only (-PuseFirebaseEmulator=true) - points
        // Firebase at the local emulator suite instead of real Google servers. Off by
        // default, so a normal debug/release build is unaffected.
        buildConfigField(
            "boolean",
            "USE_FIREBASE_EMULATOR",
            (project.findProperty("useFirebaseEmulator") == "true").toString()
        )
        buildConfigField(
            "String",
            "FEEDBACK_EMAIL",
            "\"${localProperties.getProperty("feedbackEmail", "configure-feedbackEmail-in-local.properties@example.com")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":shared-ui"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)

    debugImplementation(libs.compose.ui.tooling)
}
