import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
}

android {
    namespace = "com.androidfung.departureboard"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.androidfung.departureboard"
        minSdk = 28
        targetSdk = 37
        versionCode = 34
        versionName = "1.2.0-261025"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val props = Properties().apply {
            val propFile = project.rootProject.file("local.properties")
            if (propFile.exists()) {
                propFile.reader().use(::load)
            }
        }
        val tflApiKey = props.getProperty("TFL_API_KEY") ?: System.getenv("TFL_API_KEY") ?: ""
        buildConfigField("String", "TFL_API_KEY", "\"$tflApiKey\"")

        val nrApiKey = props.getProperty("NATIONAL_RAIL_API_KEY") ?: System.getenv("NATIONAL_RAIL_API_KEY") ?: ""
        buildConfigField("String", "NATIONAL_RAIL_API_KEY", "\"$nrApiKey\"")

        val bodsApiKey = props.getProperty("BODS_API_KEY") ?: System.getenv("BODS_API_KEY") ?: ""
        buildConfigField("String", "BODS_API_KEY", "\"$bodsApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = false
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "**/*.proto"
            )
        }
    }
}

kotlin {
    compilerOptions {
    }
}

// AppFunctions Configuration
ksp {
}

dependencies {
    // Platform / BOMs
    implementation(platform(libs.androidx.compose.bom))
    testImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    // AndroidX & Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Compose UI & Material 3
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Compose Adaptive & Navigation
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    
    // Jetpack Libraries
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    "ksp"(libs.androidx.room.compiler)
    
    // AppWidget (Glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Firebase
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)

    // Google Play Services & Billing
    implementation(libs.play.app.update.ktx)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)
    implementation(libs.play.billing)
    implementation(libs.play.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.play.services.location)
    
    // Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    "ksp"(libs.moshi.kotlin.codegen)
    
    // Utilities
    implementation(libs.androidx.appfunctions)
    "ksp"(libs.androidx.appfunctions.compiler)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // Testing
    testImplementation(libs.androidx.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kxml2)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}