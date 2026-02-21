plugins {
    java
    alias(libs.plugins.quarkus)
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))

    // REST Client dependencies
    implementation(libs.quarkus.rest.client.jackson)

    // OpenAPI Generator extension
    implementation(libs.quarkus.openapi.generator)

    // Jakarta EE dependencies for generated code
    compileOnly(libs.jakarta.ws.rs.api)
    compileOnly(libs.jakarta.annotation)
}

sourceSets {
    main {
        java {
            // Include Quarkus-generated OpenAPI client code
            srcDir(layout.buildDirectory.dir("generated-sources/openapi/src/main/java"))
        }
    }
}

// Ensure OpenAPI code generation runs before compilation
tasks.named("compileJava") {
    dependsOn("quarkusGenerateCode")
}