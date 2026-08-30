plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Generates the `@Tool` FunctionTools for the Firebase and LiteRT-LM examples.
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.android_agent_adk_litertlm_on_device_ai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.android_agent_adk_litertlm_on_device_ai"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    //ADK Core for Android
    implementation("com.google.adk:google-adk-kotlin-core-android:0.1.0")
    // Generates the `@Tool` FunctionTools for the Firebase and LiteRT-LM examples.
    ksp("com.google.adk:google-adk-kotlin-processor:0.1.0")
    // Litert-LM For Android, on-device AI
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
}
