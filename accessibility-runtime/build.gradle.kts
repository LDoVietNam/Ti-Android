plugins {
    id("java-library")
    alias(libs.plugins.kotlin.android)
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation(project(":core-model"))
    implementation(libs.kotlinx.coroutines.core)
}
