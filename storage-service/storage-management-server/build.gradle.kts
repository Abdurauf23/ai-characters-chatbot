plugins {
    alias(libs.plugins.quarkus)
    alias(libs.plugins.spotless)
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(enforcedPlatform(libs.quarkus.amazon.bom))

    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.messaging.kafka)
    implementation(libs.quarkus.smallrye.fault.tolerance)
    implementation(libs.quarkus.amazon.s3)

    implementation(libs.quarkus.config.yaml)
    implementation(libs.aws.url.connection.client)

    implementation(project(":storage-service:storage-client"))
}
