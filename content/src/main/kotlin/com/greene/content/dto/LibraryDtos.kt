package com.greene.content.dto

import com.greene.content.domain.LibraryStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class CreateLibraryRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 255, message = "name must not exceed 255 characters")
    val name: String? = null,

    val description: String? = null
)

data class UpdateLibraryRequest(
    @field:Size(max = 255, message = "name must not exceed 255 characters")
    val name: String? = null,

    // null = no change; "" = clear to null
    val description: String? = null,

    val status: LibraryStatus? = null
)

data class LibraryResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val status: LibraryStatus,
    val createdBy: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

