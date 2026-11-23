import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/** Version: v1 default is bumped by CI with -PbuildVersionCode= / -PbuildVersionName= */
val buildVersionCode: Int = (findProperty("buildVersionCode") as String?)?.toIntOrNull() ?: 10000
val buildVersionName: String = (findProperty("buildVersionName") as String?) ?: "0.1.0"
val buildFlavor: String = (findProperty("buildFlavor") as String?) ?: "dev"

android {
    namespace = "com.emrobinso.floatpic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emrobinso.floatpic"
        minSdk = 26
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName
        resourceConfigurations += listOf("id", "en")
    }

    signingConfigs {
        create("release") {
            val localProps = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun pick(env: String, prop: String, def: String? = null): String? =
                System.getenv(env) ?: localProps.getProperty(prop) ?: def

            storeFile = pick("FLOATPIC_STORE_FILE", "storeFile")?.let { file(it) }
                ?: rootProject.file("app/keystore/floatpic.p12")
            storePassword = pick("FLOATPIC_STORE_PASSWORD", "storePassword", "floatpic2026")
            storeType = pick("FLOATPIC_STORE_TYPE", "storeType", "PKCS12")
            keyAlias = pick("FLOATPIC_KEY_ALIAS", "keyAlias", "floatpic")
            keyPassword = pick("FLOATPIC_KEY_PASSWORD", "keyPassword", "floatpic2026")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Same key + same applicationId in every release => in-place update, never uninstall.
            signingConfig = signingConfigs.getByName("release")
        }
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
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
