import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Where the release key lives, read from local.properties, which is not in
 * git and never will be.
 *
 * Nothing here is required. With no key configured the release build comes
 * out unsigned and says so, which is the honest outcome: a build that
 * silently signed itself with the debug key would be one Android refuses to
 * install over the real app, and the refusal would come later and elsewhere.
 *
 * The environment is read as well, so that a machine that builds this without
 * an IDE — or a second machine, which is how the first key came to be looked
 * for and not found — has somewhere to put the answer.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingSetting(name: String, env: String): String? =
    (localProperties.getProperty(name) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStorePath = signingSetting("releaseStoreFile", "ANNAS_AGENDA_STORE_FILE")
val releaseStorePassword = signingSetting("releaseStorePassword", "ANNAS_AGENDA_STORE_PASSWORD")
val releaseKeyAlias = signingSetting("releaseKeyAlias", "ANNAS_AGENDA_KEY_ALIAS")
val releaseKeyPassword = signingSetting("releaseKeyPassword", "ANNAS_AGENDA_KEY_PASSWORD")

val releaseKeystore = releaseStorePath
    ?.let { rootProject.file(it) }
    ?.takeIf { it.isFile }

android {
    namespace = "com.alphaomegos.annasagenda"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.alphaomegos.annasagenda"
        minSdk = 24
        targetSdk = 36
        // The two used to disagree: versionCode said this was the 18th build,
        // versionName said 21.3. Nobody remembers how. From here they move
        // together, one step each per release — 21.5 will be versionCode 23 —
        // and VersionNumbersTest holds them to it.
        //
        // versionCode only ever goes up: Android refuses an update whose code
        // is lower than the one installed, so matching it down to 21 was not
        // an option.
        versionCode = 22
        versionName = "21.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Null when nothing is configured, which leaves the APK unsigned
            // rather than signing it with something that is not the key the
            // installed app was signed with.
            signingConfig = signingConfigs.findByName("release")

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Said once, at the end of configuration, so that an unsigned release APK is
 * never a surprise discovered on the phone.
 */
if (releaseKeystore == null) {
    logger.lifecycle(
        "Anna's Agenda: no release key configured, so `assembleRelease` " +
            "produces an unsigned APK. Set releaseStoreFile, " +
            "releaseStorePassword, releaseKeyAlias and releaseKeyPassword in " +
            "local.properties to sign it."
    )
}
