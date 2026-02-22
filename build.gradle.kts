plugins {
    alias(libs.plugins.spotless) apply false
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin> {
        apply(plugin = libs.plugins.spotless.get().pluginId)

        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            java {
                googleJavaFormat()
                removeUnusedImports()
                trimTrailingWhitespace()
                endWithNewline()
                forbidWildcardImports()
            }
        }
    }

    tasks.withType<GradleBuild> {
        dependsOn("spotlessCheck")

    }
}