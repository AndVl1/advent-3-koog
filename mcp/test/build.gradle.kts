plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
//    id("buildsrc.convention.kotlin-jvm")
    id("application")
    kotlin("jvm")

    // Kotlin Serialization
    alias(libs.plugins.kotlin.plugin.serialization)
}

application {
    mainClass.set("ru.andvl.mcp.github.GhMcpTestKt")
}

dependencies {
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

    // Koog LLM Client (real dependency)
    implementation(libs.koog.agents)

    // MCP SDK
    implementation(libs.mcp.kotlin)
    implementation(libs.logback.classic)
}
