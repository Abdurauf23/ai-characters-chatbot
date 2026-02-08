plugins {
    java
    alias(libs.plugins.openapi)
}

openapiProcessor {
    apiPath(layout.projectDirectory.file("src/main/resources/openapi.yaml"))
    process("client") {
        processor("io.openapiprocessor:openapi-processor-core")
        targetDir(layout.buildDirectory.dir("generated/openapi/java"))
        prop("packageName", "com.chatbot.generated.client")
    }
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/openapi/java"))
        }
    }
}

tasks.compileJava {
    dependsOn(tasks.named("processClient"))
}
