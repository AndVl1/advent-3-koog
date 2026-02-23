import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.android.application)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            implementation(libs.decompose)
            implementation(libs.decompose.compose)
            implementation(libs.essenty.lifecycle)
            implementation(libs.essenty.lifecycle.coroutines)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.markdown.renderer)
            implementation(libs.markdown.renderer.m3)

            implementation(project(":shared-models"))
        }

        val jvmMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(project(":koog-service"))
                implementation(libs.koog.agents)
                implementation(libs.koog.agents.core)
                implementation(libs.koog.ktor)
                implementation(libs.koog.prompt.executor.llms.all)
                implementation(libs.kotlinx.io.core.jvm)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.dotenv)
            }
        }

        val desktopMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.logback.classic)
                implementation(libs.slf4j.api)
                implementation(libs.koog.prompt.executor.ollama)
            }
        }

        androidMain {
            dependsOn(jvmMain)
            dependencies {
                implementation(libs.androidx.activity.compose)
                // Provide no-op SLF4J binding so Koog's KotlinLogging doesn't crash on Android ART
                implementation(libs.slf4j.nop)
            }
        }
    }
}

// Exclude logback from Android DEX — logback 1.5.x uses Class.getModule() (Java 9+ only)
// which is not available on Android's ART runtime.
configurations.matching { it.name.contains("android", ignoreCase = true) }.configureEach {
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "ch.qos.logback", module = "logback-core")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "ru.andvl.chatter.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.andvl.chatter.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${localProperties.getProperty("OPENROUTER_API_KEY", "")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "ru.andvl.chatter.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Chatter Desktop"
            packageVersion = "1.0.0"
            modules("java.sql", "jdk.unsupported")
        }
    }
}

// Configure the run task to use project root as working directory
// so .env and mcp/ JAR relative paths are resolved correctly
afterEvaluate {
    tasks.findByName("run")?.let { task ->
        (task as? JavaExec)?.workingDir = rootProject.projectDir
    }
}
