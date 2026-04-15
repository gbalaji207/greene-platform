package com.greene.training

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Minimal Spring Boot application entry point for test slices in `:training`.
 *
 * `:training` has no production @SpringBootApplication — the module is booted by `:app`.
 * @WebMvcTest (and other slice annotations) require a @SpringBootApplication in the
 * component-scan path to bootstrap correctly; this class fulfils that requirement for
 * the test classpath only.
 */
@SpringBootApplication
class TestApplication

