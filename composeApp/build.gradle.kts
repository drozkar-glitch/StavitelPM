import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
        }
    }
}

val runNum = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val ciBuildNumber = System.getenv("PROJECT_BUILD_NUMBER")?.toIntOrNull() ?: (runNum + 100)
val ciAppVersion = System.getenv("APP_VERSION")?.trim()?.takeIf { it.isNotEmpty() } ?: "1.0"

android {
    namespace = "cz.mares.stavitelpm"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.mares.stavitelpm"
        minSdk = 24
        targetSdk = 36
        versionCode = ciBuildNumber
        versionName = ciAppVersion
    }

    signingConfigs {
        val ksPath = System.getenv("CM_KEYSTORE_PATH")
        val ksPwd = System.getenv("CM_KEYSTORE_PASSWORD")
        val keyAlias = System.getenv("CM_KEY_ALIAS")
        val keyPwd = System.getenv("CM_KEY_PASSWORD")
        if (!ksPath.isNullOrBlank() && File(ksPath).exists() && !ksPwd.isNullOrBlank() && !keyAlias.isNullOrBlank()) {
            create("ciRelease") {
                storeFile = File(ksPath)
                storePassword = ksPwd
                this.keyAlias = keyAlias
                this.keyPassword = if (!keyPwd.isNullOrBlank()) keyPwd else ksPwd
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("ciRelease")?.let {
                signingConfig = it
            }
        }
        getByName("debug") {
            signingConfigs.findByName("ciRelease")?.let {
                signingConfig = it
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
