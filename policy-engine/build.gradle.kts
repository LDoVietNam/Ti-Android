plugins {
    id("java-library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation(project(":core-model"))
    implementation(project(":adapter-sdk"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
