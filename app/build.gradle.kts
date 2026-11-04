import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Fester Nachtfalter-Schlüssel (liegt im Projektordner, wird NICHT zu GitHub hochgeladen).
// Alle Versionen müssen damit unterschrieben sein, sonst lässt Android kein Update zu.
val schluesselDatei = rootProject.file("schluessel.properties")
val schluessel = Properties().apply {
    if (schluesselDatei.exists()) schluesselDatei.inputStream().use { load(it) }
}

android {
    namespace = "de.noah.nachtfalter"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "de.noah.nachtfalter"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("nachtfalter") {
            if (schluesselDatei.exists()) {
                storeFile = rootProject.file(schluessel.getProperty("storeFile"))
                storePassword = schluessel.getProperty("storePassword")
                keyAlias = schluessel.getProperty("keyAlias")
                keyPassword = schluessel.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (schluesselDatei.exists()) signingConfig = signingConfigs.getByName("nachtfalter")
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}