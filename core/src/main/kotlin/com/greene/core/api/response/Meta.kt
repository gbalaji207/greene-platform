package com.greene.core.api.response

import java.time.Instant

data class Meta(
    val timestamp: Instant = Instant.now(),
)