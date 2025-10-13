plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.grid.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.grid.app"
        minSdk = 24
        targetSdk = 35
        versionCode = generateVersionCode()
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
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
    
    // Network Protocols
    implementation(libs.smbj)
    implementation(libs.mina.core)
    implementation(libs.mina.sshd.client)
    implementation(libs.commons.net)
    
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

// KSP Configuration for Hilt
ksp {
    arg("dagger.formatGeneratedSource", "disabled")
    arg("dagger.fastInit", "enabled")
    arg("ksp.incremental", "false")
}


fun generateVersionCode(): Int {
    val versionName = "1.0.0"
    val parts = versionName.split(".")
    val major = parts[0].toInt()
    val minor = parts[1].toInt()
    val patch = parts[2].toInt()
    return major * 10000 + minor * 100 + patch
}