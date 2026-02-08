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
                // 1. REMOVE googleJavaFormat() and use this instead.
                // This is the only built-in Spotless tool that expands wildcards automatically.
                googleJavaFormat()

                // 2. Standard cleanup
                removeUnusedImports()
                trimTrailingWhitespace()
                endWithNewline()

                // 3. Optional: This will fail the build if a wildcard is ever found
                // but since palantir fixes them, this is just a safety check.
                forbidWildcardImports()
            }
        }
    }

    tasks.withType<GradleBuild> {
        dependsOn("spotlessCheck")

    }
}