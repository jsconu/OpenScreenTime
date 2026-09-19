import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Release signing comes from an untracked keystore.properties at the repo root (see
// docs/PUBLISHING.md) or from OST_* environment variables - never from a file in git. With neither
// present the release build is simply left unsigned, so CI and contributors can still build it.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "org.openscreentime.parent"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.openscreentime.parent"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        // Opt-in flag for the E2E workflow only (-PuseFirebaseEmulator=true) - points
        // Firebase at the local emulator suite instead of real Google servers. Off by
        // default, so a normal debug/release build is unaffected.
        buildConfigField(
            "boolean",
            "USE_FIREBASE_EMULATOR",
            (project.findProperty("useFirebaseEmulator") == "true").toString()
        )
    }

    signingConfigs {
        create("release") {
            signingValue("storeFile", "OST_KEYSTORE_FILE")?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("storePassword", "OST_KEYSTORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "OST_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "OST_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
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
    // Pins a current Fragment: an older one arrives transitively (via Firebase) and makes release lint
    // fail on the ActivityResult APIs, though these activities are ComponentActivity, not Fragment-based.
    implementation(libs.fragment)
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
