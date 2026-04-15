plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    // Generates synthetic no-arg constructors on @Entity/@MappedSuperclass/@Embeddable
    // classes so that Hibernate can instantiate them via reflection.
    id("org.jetbrains.kotlin.plugin.jpa")
}

group = "com.greene"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.openapi.ui)
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)

    // JWT lives in core (auth is a core concern)
    implementation(libs.bundles.jjwt)

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // PostgreSQL driver — runtimeOnly in :app for production;
    // compileOnly here so domain converters (e.g. InetAddressConverter) can reference
    // PGobject at compile time; testRuntimeOnly so integration tests in :core can connect.
    compileOnly(libs.postgresql.driver)
    testRuntimeOnly(libs.postgresql.driver)

    // Redis — rate limiting via INCR/EXPIRE
    implementation(libs.spring.boot.starter.data.redis)

    // AWS SES — transactional email (OTP delivery, welcome emails)
    implementation(libs.aws.ses)

    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)

    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}