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
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Domain exceptions thrown explicitly by any module.
     * The module controls the HTTP status, code, and message — no module-specific knowledge here.
     *
     * Uses [PlatformException.httpStatus] directly, so any status code is supported,
     * including uncommon ones such as 413 (PAYLOAD_TOO_LARGE) and 415 (UNSUPPORTED_MEDIA_TYPE)
     * thrown by ProfileService.
     */
    @ExceptionHandler(PlatformException::class)
    fun handlePlatformException(ex: PlatformException): ResponseEntity<ApiError> {
        log.debug("Platform exception: code={} status={} message={}", ex.code, ex.httpStatus, ex.message)
        return ResponseEntity.status(ex.httpStatus).body(
            ApiError(ApiError.ErrorBody(code = ex.code, message = ex.message, details = ex.details))
        )
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
     * Missing required `@RequestParam` — e.g. the `photo` field omitted from the
     * multipart request on POST /api/v1/users/me/profile/photo.
     * Spring throws this before the method body is entered.
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingRequestParam(ex: MissingServletRequestParameterException): ResponseEntity<ApiError> {
        log.debug("Missing request parameter: {}", ex.parameterName)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(code = "VALIDATION_ERROR", message = "Validation failed"))
    }

    /**
     * Path/query variable type mismatch — e.g. a non-UUID string supplied where a UUID
     * path variable is expected (`GET /api/v1/batches/not-a-uuid`).
     * Returns 400 with a field-level detail pinpointing the offending parameter.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> {
        log.debug("Type mismatch for parameter '{}': {}", ex.name, ex.message)
        val detail = when (ex.requiredType) {
            UUID::class.java -> ErrorDetail(field = ex.name, message = "invalid UUID format")
            else -> ErrorDetail(field = ex.name, message = "invalid value")
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.validation(message = "Request validation failed", details = listOf(detail)))
    }

    /**
     * Missing required multipart part — Spring 6 throws this (not [MissingServletRequestParameterException])
     * when a required `@RequestParam MultipartFile` part is absent from the multipart request.
     * Semantically equivalent to a missing parameter: map to 400 VALIDATION_ERROR.
     */
    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingRequestPart(ex: MissingServletRequestPartException): ResponseEntity<ApiError> {
        log.debug("Missing multipart part: {}", ex.requestPartName)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(code = "VALIDATION_ERROR", message = "Validation failed"))
    }

    /**
     * File exceeds the Spring multipart size limit configured in
     * `spring.servlet.multipart.max-file-size` / `max-request-size`.
     * This is thrown before the controller method is reached, so
     * ProfileService's own FILE_TOO_LARGE check never runs for these oversized uploads.
     *
     * Mapped to 413 so clients receive the same error shape regardless of whether
     * the file was rejected by Spring or by application logic.
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(ex: MaxUploadSizeExceededException): ResponseEntity<ApiError> {
        log.debug("Max upload size exceeded: {}", ex.message)
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiError.of(code = "FILE_TOO_LARGE", message = "File size must not exceed 5MB"))
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
     * Spring Security — insufficient role thrown by @PreAuthorize AOP proxy.
     * ExceptionTranslationFilter only handles filter-level access denial; method-level
     * AccessDeniedException propagates into the DispatcherServlet context and is caught here.
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ApiError> {
        log.debug("Access denied: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of("FORBIDDEN", "You do not have permission to perform this action."))
    }

    /**
     * Spring Security — authentication failure from method-level security context.
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ResponseEntity<ApiError> {
        log.debug("Authentication exception: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.of("UNAUTHORIZED", "Authentication required. Please log in."))
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