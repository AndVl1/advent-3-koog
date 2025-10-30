plugins {
    kotlin("jvm")
    id("application")

    // Kotlin Serialization
    alias(libs.plugins.kotlin.plugin.serialization)

    // Shadow JAR plugin
    alias(libs.plugins.shadow.jar)
}

application {
    mainClass.set("ru.andvl.mcp.googledocs.MainKt")
}

// Configure shadow JAR
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("googledocs")
    archiveClassifier.set("")
    archiveVersion.set("0.1.0")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "ru.andvl.mcp.googledocs.MainKt"
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "ru.andvl.mcp.googledocs.MainKt"
    }
}

group = "ru.andvl.mcp.googledocs"
version = "0.1.0"

dependencies {
    // Ktor HTTP Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Kotlin ecosystem
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // Dotenv for configuration
    implementation(libs.dotenv)

    // MCP
    implementation(libs.mcp.kotlin)

    // Logging
    implementation(libs.logback.classic)

    // Google API Client
    implementation("com.google.api-client:google-api-client:2.0.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-docs:v1-rev20220609-2.0.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
}
