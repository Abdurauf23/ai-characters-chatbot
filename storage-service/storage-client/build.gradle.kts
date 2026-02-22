plugins {
    java
    alias(libs.plugins.openapi)
}

dependencies {
    compileOnly(libs.jakarta.ws.rs.api)
    compileOnly(libs.jakarta.annotation)
    compileOnly(libs.jakarta.validation)
    compileOnly(libs.jackson.annotations)
    compileOnly(libs.jackson.databind.nullable)
}

openApiGenerate {
    generatorName.set("jaxrs-spec")
    inputSpec.set("$projectDir/src/main/resources/openapi/storage-client.yaml")
    outputDir.set("${layout.buildDirectory.get().asFile}/generated/openapi")
    configOptions.set(mapOf(
        "interfaceOnly" to "true",
        "useJakartaEe" to "true",
        "useSwaggerAnnotations" to "false",
        "useTags" to "true",
        "dateLibrary" to "java8",
        "sourceFolder" to "src/main/java",
        "apiPackage" to "com.chatbot.storage.client.api",
        "modelPackage" to "com.chatbot.storage.client.model"
    ))
}

sourceSets {
    main {
        java {
            srcDir("${layout.buildDirectory.get().asFile}/generated/openapi/src/main/java")
        }
    }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

tasks.named("test") {
    enabled = false
}

tasks.matching { it.name.startsWith("spotless") }.configureEach {
    enabled = false
}
