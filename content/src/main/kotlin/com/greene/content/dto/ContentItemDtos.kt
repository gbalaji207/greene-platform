package com.greene.content.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class SaveArticleContentRequest(
    @field:NotBlank(message = "htmlContent must not be blank")
    val htmlContent: String? = null,

    @field:Size(max = 500, message = "summary must not exceed 500 characters")
    val summary: String? = null
)

data class SaveArticleContentResponse(
    val nodeId: UUID,
    val hasFile: Boolean,
    val updatedAt: OffsetDateTime
)

