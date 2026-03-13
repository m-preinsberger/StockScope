import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

fun localProperty(name: String): String {
    return providers.gradleProperty(name).orNull ?: localProperties.getProperty(name, "")
}

android {
    namespace = "com.example.stockscope"
    compileSdk = 35

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk=24
        targetSdk=35
        buildConfigField("String", "FINNHUB_API_KEY", "\"${localProperty("FINNHUB_API_KEY")}\"")
        buildConfigField("String", "ALPHAVANTAGE_API_KEY", "\"${localProperty("ALPHAVANTAGE_API_KEY")}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperty("GEMINI_API_KEY")}\"")
    }
}

dependencies {
    implementation(libs.material)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    implementation(libs.mpandroidchart)
}
