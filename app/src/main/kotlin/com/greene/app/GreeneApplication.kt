package com.greene.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.greene"])
class GreeneApplication

fun main(args: Array<String>) {
    runApplication<GreeneApplication>(*args)
}