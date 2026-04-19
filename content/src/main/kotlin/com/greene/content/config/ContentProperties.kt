package com.greene.content.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "content")
data class ContentProperties(
    val maxArticleSizeKb: Int = 500,
    val maxImageSizeKb: Long = 2048,
)

