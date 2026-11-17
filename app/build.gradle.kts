plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

layout.buildDirectory.set(file("/Users/rohansiddhpura/.cache/reelmonster-build/app"))

android {
  namespace = "com.reelmonster.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.reelmonster.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release signing (Phase 18). The credentials live ONLY in
  // ~/.gradle/gradle.properties — never in this file, never in the repo:
  //   reelMonsterStoreFile=/Users/rohansiddhpura/reel-monster-release.keystore
  //   reelMonsterStorePassword=<keystore password>
  //   reelMonsterKeyAlias=reelmonster
  //   reelMonsterKeyPassword=<key password>
  // If those properties are absent, no signing config is created and the
  // release APK builds unsigned (it must not be shipped that way).
  signingConfigs {
    if (project.findProperty("reelMonsterStoreFile") != null) {
      create("release") {
        storeFile = file(project.findProperty("reelMonsterStoreFile") as String)
        storePassword = project.findProperty("reelMonsterStorePassword") as String
        keyAlias = project.findProperty("reelMonsterKeyAlias") as String
        keyPassword = project.findProperty("reelMonsterKeyPassword") as String
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (project.findProperty("reelMonsterStoreFile") != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      applicationIdSuffix = ".debug"
      isDebuggable = true
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Android Core & Lifecycle
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Jetpack Compose & Material 3
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)

  // DataStore Preferences
  implementation(libs.androidx.datastore.preferences)

  // Coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // Tooling & Debug
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Unit Testing
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation("org.robolectric:robolectric:4.14.1")

  // Instrumented Testing
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
