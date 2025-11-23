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

    // OpenTelemetry
    implementation(libs.koog.agents.features.opentelemetry)
    implementation(libs.opentelemetry.exporter.logging)
    implementation(libs.opentelemetry.exporter.otlp)

    // Configuration
    implementation(libs.dotenv)

    // Logging
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit.jupiter)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

// Task to run Code Modification Agent test
tasks.register<JavaExec>("runCodeModTest") {
    group = "verification"
    description = "Run Code Modification Agent test"

    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ru.andvl.chatter.koog.agents.codemod.CodeModificationAgentTestKt")

    // Pass environment variables
    environment("OPENROUTER_API_KEY", System.getenv("OPENROUTER_API_KEY") ?: "")
    environment("GITHUB_TOKEN", System.getenv("GITHUB_TOKEN") ?: "")

    // Enable detailed logging
    systemProperty("logback.configurationFile", "src/test/resources/logback-test.xml")

    dependsOn("testClasses")
}
