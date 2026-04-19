package com.greene.content.dto

import java.util.UUID

data class InlineImageUploadResponse(
    val fileId: UUID,
    val fileKey: String,
    val mimeType: String,
    val sizeBytes: Long,
)

