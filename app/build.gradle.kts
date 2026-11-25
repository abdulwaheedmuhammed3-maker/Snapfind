plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localConfig = java.util.Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.exists()) {
        configFile.inputStream().use { load(it) }
    }
}

fun configValue(name: String): String =
    (providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: localConfig.getProperty(name)
        ?: "").trim()

fun String.asBuildConfigLiteral(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.snapfind.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.snapfind.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        buildConfigField("String", "SUPABASE_URL", configValue("SUPABASE_URL").asBuildConfigLiteral())
        buildConfigField("String", "SUPABASE_ANON_KEY", configValue("SUPABASE_ANON_KEY").asBuildConfigLiteral())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
