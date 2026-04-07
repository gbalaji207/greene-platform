package com.greene.core.exception

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
 */
class PlatformException(
    val code: String,
    override val message: String,
    val httpStatus: HttpStatus,
) : RuntimeException(message)