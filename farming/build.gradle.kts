plugins {
    id("buildsrc.convention.kotlin-jvm")
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
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}