package com.greene.core.web


import com.greene.core.api.error.ApiError
import com.greene.core.api.error.ErrorDetail
import com.greene.core.exception.PlatformException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Domain exceptions thrown explicitly by any module.
     * The module controls the HTTP status, code, and message — no module-specific knowledge here.
     */
    @ExceptionHandler(PlatformException::class)
    fun handlePlatformException(ex: PlatformException): ResponseEntity<ApiError> {
        log.debug("Platform exception: code={} status={} message={}", ex.code, ex.httpStatus, ex.message)
        return ResponseEntity.status(ex.httpStatus).body(ApiError.of(code = ex.code, message = ex.message))
    }

    /**
     * DB unique-constraint violations — safety net for cases where AuthService did not
     * perform an explicit pre-flight check (should be rare but must never surface a 500).
     *
     * Constraint name matching is case-insensitive against the root-cause message.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ApiError> {
        val msg = ex.rootCause?.message?.lowercase() ?: ""
        log.debug("Data integrity violation: {}", msg)
        return when {
            "uq_users_phone" in msg -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("PHONE_ALREADY_REGISTERED", "An account with this phone number already exists"))
            "uq_users_email" in msg -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("EMAIL_ALREADY_ACTIVE", "An account with this email is already registered"))
            else -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CONFLICT", "A data conflict occurred"))
        }
    }

    /**
     * Bean Validation failures (@Valid on request DTOs).
     * Returns 400 with field-level details.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val details = ex.bindingResult.fieldErrors.map { fe ->
            ErrorDetail(field = fe.field, message = fe.defaultMessage ?: "Invalid value")
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.validation(message = "Validation failed", details = details))
    }

    /**
     * Malformed JSON body — e.g. missing required fields that aren't nullable,
     * or completely unparseable JSON.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(ex: HttpMessageNotReadableException): ResponseEntity<ApiError> {
        log.debug("Unreadable request body: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(code = "VALIDATION_ERROR", message = "Validation failed"))
    }

    @ExceptionHandler(HttpMessageConversionException::class)
    fun handleMessageConversion(ex: HttpMessageConversionException): ResponseEntity<ApiError> {
        log.debug("Message conversion error: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(code = "MALFORMED_REQUEST", message = "Request body is missing or malformed"))
    }

    /**
     * HTTP method not supported — e.g. POST to a GET-only endpoint.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ApiError> {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
            ApiError.of(
                code = "METHOD_NOT_ALLOWED",
                message = "HTTP method '${ex.method}' is not supported for this endpoint"
            )
        )
    }

    /**
     * No handler found — endpoint does not exist.
     * Spring Boot 3.x throws NoResourceFoundException for 404s (not NoHandlerFoundException).
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(ex: NoResourceFoundException): ResponseEntity<ApiError> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of(code = "ENDPOINT_NOT_FOUND", message = "The requested endpoint does not exist"))
    }

    /**
     * Catch-all for any unhandled exception.
     * Logs the full stack trace server-side, returns a generic 500 to the client.
     * Never expose internal details in the response.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiError> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of(code = "INTERNAL_ERROR", message = "An unexpected error occurred"))
    }
}