// Explicit import: in the Kotlin DSL a bare `java.util.Properties` resolves
// against the `java` project extension, not the JDK package.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// Release signing is read from an UNTRACKED keystore.properties so no signing
// material ever enters version control. Create it once with:
//
//   keytool -genkeypair -v -keystore wellness-release.jks -alias wellness \
//           -keyalg RSA -keysize 4096 -validity 10000
//
// then write android/keystore.properties:
//   storeFile=C:/path/to/wellness-release.jks
//   storePassword=...
//   keyAlias=wellness
//   keyPassword=...
//
// Without that file the release build stays unsigned and `assembleDebug` is
// unaffected — the installer falls back to the debug APK.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.wellnesscompanion.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wellnesscompanion.app"
        minSdk = 26
        targetSdk = 34
        // Kept in step with windows/package.json and the installer artifact name.
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Puts the shared crypto test-vector fixture (shared/crypto-vectors.json,
    // repo-root-relative) onto the pure-JVM unit-test classpath as a raw
    // resource, so VectorsSmokeTest (and Task 2's real derivation tests) can
    // read it via `javaClass.classLoader.getResourceAsStream(...)`.
    sourceSets {
        getByName("test") {
            resources.srcDir("$rootDir/../shared")
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-android-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.google.code.gson:gson:2.11.0")

    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Sync — OkHttp WebSocket client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Unit tests (pure JVM, src/test) — security-hardening crypto suites.
    testImplementation("junit:junit:4.13.2")
}
