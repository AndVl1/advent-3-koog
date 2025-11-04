import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
//    alias(libs.plugins.shadow.jar)
}

group = "ru.andvl.chatter"
version = "1.0.1"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Markdown renderer
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)

    // Project modules (must be before koog to ensure proper dependency resolution)
    implementation(project(":koog-service"))
    implementation(project(":shared-models"))

    // Koog AI Agents (with all runtime dependencies explicitly listed)
    implementation(libs.koog.agents)
    implementation(libs.koog.agents.core)
    implementation(libs.koog.ktor)
    implementation(libs.koog.prompt.executor.llms.all)

    // Kotlinx libraries - required by koog at runtime (using JVM-specific versions)
//    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.datetime.jvm)
    implementation(libs.kotlinx.io.core.jvm)
    implementation(libs.kotlinx.serialization.json)

    // Ktor client - required by koog
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Logging
    implementation(libs.logback.classic)
    implementation(libs.slf4j.api)
}

compose.desktop {
    application {
        mainClass = "ru.andvl.chatter.desktop.MainKt"

        // Ensure all dependencies are included in runtime classpath
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Chatter Desktop"
            packageVersion = "1.0.0"

            // Include all dependencies in the package
            modules("java.sql", "jdk.unsupported")

            macOS {
                iconFile.set(project.file("icon.icns"))
            }
            windows {
                iconFile.set(project.file("icon.ico"))
            }
            linux {
                iconFile.set(project.file("icon.png"))
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}
