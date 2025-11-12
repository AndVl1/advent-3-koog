plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":shared-models"))
    implementation(libs.koog.ktor)
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)

    // RAG dependencies
    implementation(libs.koog.embeddings.llm)
    implementation(libs.koog.vector.storage)
    implementation(libs.koog.prompt.executor.ollama)

    // Logging
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test.junit)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
