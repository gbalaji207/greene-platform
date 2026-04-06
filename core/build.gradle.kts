plugins {
    id("buildsrc.convention.kotlin-jvm")
}

group = "com.greene"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}