plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.androidfung.departureboard.features.ai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":app"))

    // LiteRT-LM (Heavy on-device generative LLM libraries ~45MB) isolated in this on-demand module
    implementation(libs.litertlm.android)
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
