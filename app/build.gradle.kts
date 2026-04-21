import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val envProps = Properties()
val envFile = rootProject.file(".env")
if (envFile.exists()) {
    envFile.inputStream().use { envProps.load(it) }
}

fun env(name: String, defaultValue: String = ""): String {
    return envProps.getProperty(name)
        ?: System.getenv(name)
        ?: defaultValue
}

fun buildConfigString(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.example.gestion_mobilek"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.gestion_mobilek"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MARIADB_HOST", buildConfigString(env("MARIADB_HOST")))
        buildConfigField("int", "MARIADB_PORT", env("MARIADB_PORT", "0").toIntOrNull()?.toString() ?: "0")
        buildConfigField("String", "MARIADB_USER", buildConfigString(env("MARIADB_USER")))
        buildConfigField("String", "MARIADB_PASSWORD", buildConfigString(env("MARIADB_PASSWORD")))
        buildConfigField("String", "MARIADB_DATABASE", buildConfigString(env("MARIADB_DATABASE")))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += "/META-INF/AL2.0"
            excludes += "/META-INF/LGPL2.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}