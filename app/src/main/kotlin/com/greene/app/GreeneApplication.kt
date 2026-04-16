package com.greene.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.greene"])
@EntityScan(basePackages = ["com.greene"])
@EnableJpaRepositories(basePackages = ["com.greene"])
class GreeneApplication

fun main(args: Array<String>) {
    runApplication<GreeneApplication>(*args)
}