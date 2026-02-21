pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "ai-character-chatbot"

include("storage-service")
include("storage-service:storage-management-server")
include("storage-service:storage-client")
