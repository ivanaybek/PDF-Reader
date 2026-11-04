import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Saf Kotlin/JVM XLSX okuma motoru. Android bağımlılığı yoktur; bu sayede JVM üzerinde
// hızlıca test edilebilir. XmlPullParser arayüzü Android çatısında hazır bulunduğu için
// yalnızca derleme zamanında (compileOnly) eklenir.
plugins {
    alias(libs.plugins.kotlin.jvm)
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
    compileOnly(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(libs.junit)
}

tasks.test {
    maxHeapSize = "1g"
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
