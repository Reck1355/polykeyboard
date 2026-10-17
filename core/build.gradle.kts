plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slide.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField(
            "boolean",
            "ENGLISH_ONLY",
            if (((findProperty("polykeyboardTier") as String?)?.lowercase() ?: "full") == "full") "false" else "true",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
}
