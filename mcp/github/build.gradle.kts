plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    kotlin("jvm")
    id("application")

    // Kotlin Serialization
    alias(libs.plugins.kotlin.plugin.serialization)

    // Shadow JAR plugin
    alias(libs.plugins.shadow.jar)
}

application {
    mainClass.set("ru.andvl.mcp.github.MainKt")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "ru.andvl.mcp.github.MainKt"
    }
}

// Configure shadow JAR
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("github")
    archiveClassifier.set("")
    archiveVersion.set("0.1.0")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "ru.andvl.mcp.github.MainKt"
    }
}

group = "ru.andvl.mcp.github"
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

    implementation(libs.mcp.kotlin)

    implementation(libs.logback.classic)
}
