plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    application
}

application {
    mainClass = "ru.andvl.chatter.cli.CliAppKt"
}

dependencies {
    implementation(project(":shared-models"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    // Manual SSE client implementation - no dedicated Ktor client SSE plugin available
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.clikt)
    implementation(libs.jline.terminal)
    implementation(libs.jline.reader)
    implementation(libs.logback.classic)
    implementation("io.ktor:ktor-client-logging:3.3.0")

    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
