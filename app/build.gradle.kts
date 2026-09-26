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

fun settingOrNull(name: String, env: String): String? =
    localProperties.getProperty(name) ?: System.getenv(env)

/**
 * A path or an alias. Blank means nobody filled it in: neither names anything.
 */
fun requiredSetting(name: String, env: String): String? =
    settingOrNull(name, env)?.takeIf { it.isNotBlank() }

/**
 * A password, where blank and absent are different answers.
 *
 * A key entry is allowed to have no password at all. Reading blank as absent
 * would turn that correct answer into "no key configured" and produce an
 * unsigned APK without a word, with the failure arriving on a phone as a
 * refused install rather than here. Present-and-empty is a value; only absent
 * is absence.
 *
 * This project's key does have a password. An earlier version of this comment
 * said otherwise, on no evidence: a password typed into a hidden prompt looks
 * exactly like one that was not typed at all.
 */
fun passwordSetting(name: String, env: String): String? = settingOrNull(name, env)

val releaseStorePath = requiredSetting("releaseStoreFile", "ANNAS_AGENDA_STORE_FILE")
val releaseStorePassword = passwordSetting("releaseStorePassword", "ANNAS_AGENDA_STORE_PASSWORD")
val releaseKeyAlias = requiredSetting("releaseKeyAlias", "ANNAS_AGENDA_KEY_ALIAS")
val releaseKeyPassword = passwordSetting("releaseKeyPassword", "ANNAS_AGENDA_KEY_PASSWORD")

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

            // On since 0101. The rules it needs went in separately, in 0081,
            // so that this stayed a one-line change that can be undone in one
            // line — and so the first minified build and the first signed
            // build were not the same build.
            //
            // Nothing in `./gradlew test` exercises this: the tests build the
            // debug variant, where R8 does not run. What this flag does is
            // checked by installing the release APK and looking, which is why
            // the version number deliberately does not move with it.
            isMinifyEnabled = true
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

    testOptions {
        unitTests {
            // Robolectric reads the app's real resources — strings, the locale
            // list, the backup rules — rather than a stub. Without this, every
            // test that touches a resource fails in a way that looks like a
            // missing translation.
            isIncludeAndroidResources = true
        }
    }

    // Tests that describe a contract of this app rather than of a platform go
    // in src/sharedTest and run twice: on the JVM under Robolectric with
    // `test`, and on a real device with `androidTest`. One file, two runners.
    //
    // The alternative — a copy in each source set — drifts, and the drift
    // shows up on the run nobody does often, which is the device one.
    //
    // `kotlin.srcDir`, not `java.srcDir`. The java one is accepted, warns that
    // it is deprecated, and then does not put those Kotlin files in front of
    // the Kotlin compiler. Nothing failed: the folder simply did not exist as
    // far as the build was concerned, for four patches, while the test count
    // quietly stayed where it was. SharedTestsAreOnTheClasspathTest exists so
    // that cannot happen silently again.
    sourceSets {
        getByName("test").kotlin.srcDir("src/sharedTest/java")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
    }
}

// Android 36's framework asks the JVM for a shared-memory file descriptor while
// the test environment is starting, and Robolectric answers that by reaching
// into jdk.internal.access.SharedSecrets. Since Java 9 that package is sealed
// inside java.base, so the call fails before any test of ours has run:
//
//   IllegalAccessException: ... cannot access class jdk.internal.access.SharedSecrets
//   (in module java.base) because module java.base does not export
//   jdk.internal.access to unnamed module
//
// Opening exactly that one package, and nothing else, is what the message asks
// for. It affects only the unit-test JVM — not the app, not the build.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")

    // The instrumented sources are compiled on every unit-test run, even
    // though they are not run here.
    //
    // Nothing else compiles them: this task ignores them, and
    // `connectedDebugAndroidTest` needs a device, so they are built once in a
    // while rather than once a day, and they rot silently. Patch 0060 added a
    // use of AppThemeMode to MainMenuContentTest without its import, and that
    // file did not compile for the next thirty-seven patches.
    //
    // Compiling is not running, and this does not pretend otherwise. It only
    // means a test that cannot build says so the same day rather than in a
    // month.
    dependsOn("compileDebugAndroidTestKotlin")
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

    // Tests that need a Context, run on the JVM rather than on a device. See
    // app/src/test/resources/robolectric.properties for which Android they get.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)

    // Compose's own test rules, on the JVM. The same artifact androidTest
    // uses; ui-test-manifest below supplies the activity both of them need.
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Dispatchers.setMain, so a view model's own scope has somewhere to run
    // while a test blocks the thread waiting for it.
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)

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
