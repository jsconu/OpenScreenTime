import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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


    // Two ways to run OpenScreenTime, and a build is one or the other - never both (see
    // docs/LOCAL_AND_CLOUD.md):
    //
    //   local  - everything stays on this phone. No account, no pairing, no Firebase SDK compiled
    //            in at all: the :cloud module is simply not a dependency of this flavor, so there
    //            is no cloud code in the APK to trust or audit. This is what gets published.
    //   cloud  - today's behaviour: a parent's phone and a child's phone paired through a Firebase
    //            project you run yourself, which is what makes remote limits and locking possible.
    flavorDimensions += "backend"
    productFlavors {
        create("local") {
            dimension = "backend"
            // No suffix on purpose: this is the flavour that gets released, and F-Droid derives the
            // git tag to build from versionName. "0.2.0-local" would have no tag to match.
        }
        create("cloud") {
            dimension = "backend"
            versionNameSuffix = "-cloud"
        }
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
    // Optional "unlock with fingerprint or screen lock" for this app (never used on the kid app).
    implementation(libs.biometric)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)

    // The only edge where cloud code enters the build. A local build resolves none of this.
    "cloudImplementation"(project(":cloud"))

    debugImplementation(libs.compose.ui.tooling)
}
