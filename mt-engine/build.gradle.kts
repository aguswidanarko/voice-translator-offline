import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Provided at runtime by onnxruntime-android in the app module.
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.20.0")

    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    systemProperty("java.library.path", "")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
