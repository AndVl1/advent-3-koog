plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlinx.rpc.plugin)
    alias(libs.plugins.shadow.jar)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":koog-service"))
    implementation(project(":shared-models"))
    implementation(libs.ktor.server.di)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.sse)
    implementation(libs.kotlinx.rpc.krpc.ktor.server)
    implementation(libs.kotlinx.rpc.krpc.ktor.client)
    implementation(libs.koog.ktor)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.dotenv)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

tasks {
    processResources {
        from("../config.properties") {
            into("config.properties")
        }
    }

    named<JavaExec>("run") {
        environment("GOOGLE_API_KEY", System.getenv("GOOGLE_API_KEY") ?: project.findProperty("GOOGLE_API_KEY") ?: "")
        environment("OPENROUTER_API_KEY", System.getenv("OPENROUTER_API_KEY") ?: project.findProperty("OPENROUTER_API_KEY") ?: "")
        // Также передаем как системные свойства
        systemProperty("GOOGLE_API_KEY", project.findProperty("GOOGLE_API_KEY") ?: "")
        systemProperty("OPENROUTER_API_KEY", project.findProperty("OPENROUTER_API_KEY") ?: "")
    }
}
