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
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.cli)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
