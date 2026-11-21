// Modulo Kotlin puro (nessuna dipendenza Android): modello, prezzi, pertinenza, estrattori, regole dei siti, API.
// Si testa sulla JVM, anche senza SDK Android: vedi tools/core-test.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.jsoup)
    testImplementation(libs.junit)
}
