plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("kapt")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}