pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TiAndroid"

// ── Core Modules ──
include(":app")
include(":core-model")
include(":runtime-core")
include(":transport")
include(":accessibility-runtime")
include(":vision-runtime")
include(":intent-runtime")
include(":notification-runtime")
include(":adapter-sdk")
include(":policy-engine")
include(":secure-storage")
include(":persistence")
include(":telemetry")

// ── Adapters ──
include(":adapters:generic")
include(":adapters:telegram")

// ── Testing ──
include(":testing:fake-gateway")
include(":testing:fixture-app")
include(":testing:accessibility-dumps")
include(":testing:replay-runner")
