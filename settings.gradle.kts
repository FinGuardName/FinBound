pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "finguard"

include(
    "backend:core-api",
    "backend:gateway",
    "backend:agent",
    "backend:mock-finance",
)
