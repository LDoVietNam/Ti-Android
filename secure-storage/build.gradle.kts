plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
}
android { namespace = "ti.android.secure"; compileSdk = 35
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":core-model")); implementation(libs.security.crypto); implementation(libs.kotlinx.coroutines.core) }
