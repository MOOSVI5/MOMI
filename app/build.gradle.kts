import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.Duration

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.luma.xkpqrm"
    minSdk = 24
    targetSdk = 36
    versionCode = 12
    versionName = "2.1"

    val dotEnvMap = buildMap<String, String> {
      val envFile = rootProject.file(".env")
      if (envFile.exists()) {
        envFile.readLines()
          .map { it.trim() }
          .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
          .forEach { line ->
            val idx = line.indexOf('=')
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim().removeSurrounding("\"")
            if (k.isNotEmpty() && v.isNotEmpty()) put(k, v)
          }
      }
    }
    fun resolveEnv(key: String, fallback: String = ""): String {
      val fromEnv = System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
      val fromFile = dotEnvMap[key]?.takeIf { it.isNotEmpty() }
      return (fromEnv ?: fromFile ?: fallback).replace("\\", "\\\\").replace("\"", "\\\"")
    }

    val startIoAppId = resolveEnv("STARTIO_APP_ID", "210719378")
    val admobAppId = resolveEnv("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
    val admobBannerUnitId = resolveEnv("ADMOB_BANNER_UNIT_ID", "ca-app-pub-3940256099942544/6300978111")
    val admobMrecUnitId = resolveEnv("ADMOB_MREC_UNIT_ID", "ca-app-pub-3940256099942544/6300978111")
    val admobInterstitialUnitId = resolveEnv("ADMOB_INTERSTITIAL_UNIT_ID", "ca-app-pub-3940256099942544/1033173712")
    val monetagDirectLinkUrl = resolveEnv("MONETAG_DIRECT_LINK_URL", "")
    val supabaseUrl = resolveEnv("SUPABASE_URL", "https://qotuvugkapaspowhbsbf.supabase.co")
    val supabaseAnonKey = resolveEnv("SUPABASE_ANON_KEY", "sb_publishable_LbrQBbU_4qH18qjopmNwIg_ALsMhlln")

    manifestPlaceholders["startIoAppId"] = startIoAppId
    manifestPlaceholders["admobAppId"] = admobAppId
    buildConfigField("String", "STARTIO_APP_ID", "\"$startIoAppId\"")
    buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
    buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBannerUnitId\"")
    buildConfigField("String", "ADMOB_MREC_UNIT_ID", "\"$admobMrecUnitId\"")
    buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$admobInterstitialUnitId\"")
    buildConfigField("String", "MONETAG_DIRECT_LINK_URL", "\"$monetagDirectLinkUrl\"")
    buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
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
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.addAll(
    listOf(
      "FIREBASE_APPCHECK_DEBUG_TOKEN",
      "STARTIO_APP_ID",
      "ADMOB_APP_ID",
      "ADMOB_BANNER_UNIT_ID",
      "ADMOB_MREC_UNIT_ID",
      "ADMOB_INTERSTITIAL_UNIT_ID",
      "MONETAG_DIRECT_LINK_URL",
      "SUPABASE_URL",
      "SUPABASE_ANON_KEY"
    )
  )
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.startapp.inapp.sdk)
  implementation(libs.play.services.ads)
  implementation(libs.billing.ktx)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui.compose)
  implementation(libs.androidx.media3.transformer)
  implementation(libs.androidx.media3.effect)
  implementation(libs.androidx.media3.common)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<Test> {
  failFast = true
  timeout.set(Duration.ofMinutes(2))
  testLogging {
    events("passed", "failed", "skipped")
  }
}
