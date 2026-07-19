plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

import java.util.Properties

android {
  namespace = "com.example"
  compileSdk { version = release(37) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.docuscan.ocr"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "2.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // ponytail: ship ARM-only .so (phones/tablets). Drops x86/x86_64 (~emulator-only)
    // which is ~90% of the OpenCV native bloat. Keeps full OpenCV API, zero detector changes.
    ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
  }

  signingConfigs {
    create("release") {
      // 1) GitHub Actions env vars (optional)
      val envKeystorePath = System.getenv("KEYSTORE_PATH")
      val envStorePass = System.getenv("STORE_PASSWORD")
      val envKeyAlias = System.getenv("KEY_ALIAS")
      val envKeyPass = System.getenv("KEY_PASSWORD")

      // 2) Committed keystore + app/keystore.properties (no GitHub secrets needed)
      val propsFile = file("${projectDir}/keystore.properties")
      val props: Properties? = if (propsFile.exists()) {
        Properties().apply { load(propsFile.inputStream()) }
      } else null
      val bundledKeystore = file("${projectDir}/upload-keystore.p12")

      val keystoreFile: File? = when {
        envKeystorePath != null -> file(envKeystorePath)
        bundledKeystore.exists() -> bundledKeystore
        else -> null
      }

      if (keystoreFile != null && keystoreFile.exists()) {
        storeFile = keystoreFile
        storeType = "PKCS12"
        storePassword = envStorePass ?: props?.getProperty("storePassword") ?: "DocuScanRelease123"
        keyAlias = envKeyAlias ?: props?.getProperty("keyAlias") ?: "upload"
        keyPassword = envKeyPass ?: props?.getProperty("keyPassword") ?: "DocuScanRelease123"
      } else {
        // Fallback: debug signing so a local build still works without a keystore
        val debugConfig = signingConfigs.getByName("debug")
        storeFile = debugConfig.storeFile
        storePassword = debugConfig.storePassword
        keyAlias = debugConfig.keyAlias
        keyPassword = debugConfig.keyPassword
      }
    }
    create("debugConfig") {
      val localDebugKeystore = file("${rootDir}/debug.keystore")
      if (localDebugKeystore.exists()) {
        storeFile = localDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      } else {
        val debugConfig = signingConfigs.getByName("debug")
        storeFile = debugConfig.storeFile
        storePassword = debugConfig.storePassword
        keyAlias = debugConfig.keyAlias
        keyPassword = debugConfig.keyPassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.opencv)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
