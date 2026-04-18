plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.kotlin.plugin.jpa")
}

group = "com.greene"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}