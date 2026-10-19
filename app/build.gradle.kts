import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing credentials are loaded from keystore.properties at the repo root.
// That file (and the keystore itself) are intentionally NOT checked into version control.
// Until keystore.properties exists, release builds are produced UNSIGNED so the build
// still works; once you drop the file in, release builds are signed automatically.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseKeystore = keystorePropertiesFile.exists()
if (hasReleaseKeystore) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Single source of truth for the app version. versionName feeds BuildConfig.VERSION_NAME
// (shown in Settings) and versionCode is derived from it below. Bump this one value.
val appVersionName = "1.2.0"

android {
    namespace = "com.defnf.grid"
    // 37 is required by Compose 1.12 / Material 3 1.5 (targetSdk stays at the Play-required level)
    compileSdk = 37

    defaultConfig {
        applicationId = "com.defnf.grid"
        minSdk = 24
        targetSdk = 36
        versionCode = generateVersionCode()
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Backport java.time / java.nio.file APIs so they work on minSdk 24 (API 26 features).
        isCoreLibraryDesugaringEnabled = true
    }

    lint {
        // Zero tolerance: every lint warning is promoted to an error and fails the build,
        // on both debug and release. Combined with compilerOptions.allWarningsAsErrors, nothing
        // warning-level can slip through.
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true

        // --- Deliberately excluded checks (NOT correctness/quality issues) ---
        // TrustAllX509TrustManager fires inside the Apache MINA jar (third-party, not our
        // source). The app talks SSH (sshd-sftp) and SMB, not that library's TLS path, so it
        // is a non-applicable false positive we cannot fix in dependency bytecode.
        disable += "TrustAllX509TrustManager"
        // The following are informational "a newer version is available" advisories. They are
        // not defects, they re-fire every time any dependency publishes a release, and clearing
        // them can require major-version migrations that need runtime verification.
        // Dependencies are reviewed/bumped out-of-band instead, so
        // these are excluded from the zero-tolerance gate to keep it stable and meaningful.
        disable += "NewerVersionAvailable"
        disable += "GradleDependency"
        disable += "AndroidGradlePluginVersion"
        disable += "OldTargetApi"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // GridLog delegates to android.util.Log, which throws "not mocked" under plain JVM unit
            // tests. Returning defaults lets logging be a no-op in tests instead of forcing every
            // test that touches a logging code path to stub the framework.
            isReturnDefaultValues = true
        }
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
}

kotlin {
    compilerOptions {
        // Zero tolerance for warnings: any Kotlin compiler warning fails the build.
        // (jvmTarget follows android.compileOptions.targetCompatibility with built-in Kotlin.)
        allWarningsAsErrors = true
        // Kotlin 2.2+ default: an annotation on a constructor property (e.g. Hilt's
        // @ApplicationContext) applies to the parameter and the property/field.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("dagger.formatGeneratedSource", "disabled")
    arg("dagger.fastInit", "enabled")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.navigation.compose)
    
    // Additional Compose dependencies
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.palette.ktx) // For album art color extraction
    
    // Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    
    // Data Storage
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Background Work
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    
    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    
    // Markdown Rendering
    implementation(libs.markwon.core) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.markwon.html) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    
    // Video Playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    
    // Network Protocols
    implementation(libs.smbj)
    implementation(libs.mina.core)
    implementation(libs.mina.sshd.client)
    
    // Archive Handling
    implementation(libs.commons.compress)
    implementation(libs.junrar)
    implementation(libs.xz)

    // EPUB Handling
    implementation(libs.jsoup) // For HTML parsing in EPUB and ZIP handling
    implementation(libs.androidx.compose.foundation) // For HorizontalPager (version from Compose BOM)

    // Core library desugaring (java.time / java.nio.file backport for minSdk 24)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}


fun generateVersionCode(): Int {
    val parts = appVersionName.split(".")
    val major = parts[0].toInt()
    val minor = parts[1].toInt()
    val patch = parts[2].toInt()
    return major * 10000 + minor * 100 + patch
}