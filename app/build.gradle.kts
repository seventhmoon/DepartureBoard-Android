import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    jacoco
}

jacoco {
    toolVersion = "0.8.13"
}

android {
    namespace = "com.androidfung.departureboard"
    compileSdk {
        version = release(37)
    }

    androidResources {
        generateLocaleConfig = true
    }

    dynamicFeatures += setOf(":features:ai_assistant")

    defaultConfig {
        applicationId = "com.androidfung.departureboard"
        minSdk = 28
        targetSdk = 37
        versionCode = 50
        versionName = "1.3.2-261106"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

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
        disable += setOf("InvalidFragmentVersionForActivityResult", "MissingTranslation")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
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

// Robolectric's sandbox classloader can produce orphan probe entries; drop them.
tasks.withType<Test>().matching { it.name.endsWith("UnitTest") }.configureEach {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Usage: ./gradlew :app:jacocoTestReport  -> build/reports/jacoco/jacocoTestReport/index.html
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates a JaCoCo coverage report for the debug unit tests."

    val buildRoot = project.layout.buildDirectory
    val generatedOrSynthetic = arrayOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/kotlin/**"
    )
    classDirectories.setFrom(
        files(
            // AGP 9.x new build layout: Kotlin classes compile here (tmp/kotlin-classes no longer exists).
            fileTree("${buildRoot.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
                exclude(*generatedOrSynthetic)
            },
            fileTree("${buildRoot.get()}/intermediates/javac/debug") {
                exclude(*generatedOrSynthetic)
            },
            // Legacy KGP layout — matches nothing on AGP 9+, kept as a fallback for older toolchains.
            fileTree("${buildRoot.get()}/tmp/kotlin-classes/debug") {
                exclude(*generatedOrSynthetic)
            }
        )
    )
    sourceDirectories.setFrom(files("${project.projectDir}/src/main/java"))
    executionData.setFrom(
        files("${buildRoot.get()}/jacoco/testDebugUnitTest.exec")
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// AppFunctions Configuration
ksp {
}

dependencies {
    // Platform / BOMs
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    // AndroidX & Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
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
    implementation(libs.play.review.ktx)
    implementation(libs.play.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.play.services.location)
    
    // Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    "ksp"(libs.moshi.kotlin.codegen)
    
    // AppFunctions & ProfileInstaller
    implementation(libs.androidx.appfunctions)
    "ksp"(libs.androidx.appfunctions.compiler)
    implementation(libs.androidx.profileinstaller)

    // Google Play Services LiteRT (0 KB native runtime bloat in base)
    implementation(libs.play.services.litert)
    implementation(libs.play.feature.delivery.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    // Testing
    testImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
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