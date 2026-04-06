plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("org.springframework.boot")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":training"))
    implementation(project(":farming"))
    implementation(project(":journal"))
    implementation(project(":content"))
    implementation(project(":notifications"))
    implementation(project(":support"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    runtimeOnly(libs.postgresql.driver)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
}

springBoot {
    mainClass = "com.greene.app.GreeneApplicationKt"
}