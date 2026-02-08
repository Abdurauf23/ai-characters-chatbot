pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "ai-character-chatbot"

include("storage-service")
//include("storage-service:rustfs-client")
include("storage-service:rustfs-server")