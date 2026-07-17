plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ti.android.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    sourceSets["main"].assets.srcDir(rootProject.file("termux/bin"))
    sourceSets["main"].assets.exclude("tirouter-arm64-linux")

    tasks.named("preBuild").configure {
        dependsOn("verifyTiRouterArtifact")
    }

    defaultConfig {
        applicationId = "ti.android.runtime"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        // Build config fields
        buildConfigField("String", "TI_ENV", "\"dev\"")
        buildConfigField("String", "TI_DEFAULT_GATEWAY_URL", "\"wss://gateway.example.local/device\"")
        buildConfigField("String", "TI_PROTOCOL_VERSION", "\"1.0\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "TI_ENV", "\"dev\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "TI_ENV", "\"production\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.register("verifyTiRouterArtifact") {
    val artifact = rootProject.file("termux/bin/tirouter-android-arm64")
    inputs.file(artifact)
    doLast {
        check(artifact.isFile) {
            "Thiếu TiRouter Android ARM64. Chạy termux/build-tools/build-tirouter-android.ps1 trước khi build APK."
        }
        val bytes = artifact.readBytes()
        check(bytes.size > 20 && bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() &&
            bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
            "Artifact TiRouter không phải ELF Android/Linux."
        }
        check(bytes[18] == 0xB7.toByte() && bytes[19] == 0x00.toByte()) {
            "Artifact TiRouter không phải ARM64."
        }
    }
}

dependencies {
    // ── Compose ──
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // ── Android ──
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    // ── DI ──
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Network ──
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // ── Storage ──
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // ── Serialization ──
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ── Logging ──
    implementation(libs.timber)

    // ── Internal Modules ──
    implementation(project(":core-model"))
    implementation(project(":runtime-core"))
    implementation(project(":transport"))
    implementation(project(":accessibility-runtime"))
    implementation(project(":vision-runtime"))
    implementation(project(":intent-runtime"))
    implementation(project(":notification-runtime"))
    implementation(project(":adapter-sdk"))
    implementation(project(":policy-engine"))
    implementation(project(":secure-storage"))
    implementation(project(":persistence"))
    implementation(project(":telemetry"))
}
