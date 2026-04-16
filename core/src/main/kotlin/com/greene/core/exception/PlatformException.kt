package com.greene.core.exception

import com.greene.core.api.error.ErrorDetail
import org.springframework.http.HttpStatus

/**
 * Base exception for all domain modules.
 *
 * Usage from any module:
 *   throw PlatformException("BATCH_NOT_FOUND", "Batch with id $id not found", HttpStatus.NOT_FOUND)
 *   throw PlatformException("TICKET_LIMIT_EXCEEDED", "Max 3 open tickets allowed", HttpStatus.UNPROCESSABLE_ENTITY)
 *
 * The [code] field is a SCREAMING_SNAKE_CASE business error code included in the error response.
 * Never expose internal class names or stack trace information in [message].
 *
 * Optional [details] carries field-level validation errors (e.g. cross-field checks performed
 * in the service layer that cannot be expressed as bean-validation constraints).
 */
class PlatformException(
    val code: String,
    override val message: String,
    val httpStatus: HttpStatus,
    val details: List<ErrorDetail> = emptyList(),
) : RuntimeException(message)