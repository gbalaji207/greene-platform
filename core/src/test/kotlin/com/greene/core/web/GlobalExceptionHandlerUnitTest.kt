package com.greene.core.web

import com.greene.core.exception.PlatformException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.servlet.resource.NoResourceFoundException

class GlobalExceptionHandlerUnitTest {

    private val handler = GlobalExceptionHandler()

    // ── PlatformException ────────────────────────────────────────────────────

    @Test
    fun `platform exception maps to its declared http status and code`() {
        val ex = PlatformException(
            code = "BATCH_NOT_FOUND",
            message = "Batch with id abc not found",
            httpStatus = HttpStatus.NOT_FOUND,
        )

        val response = handler.handlePlatformException(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        with(response.body!!.error) {
            assertEquals("BATCH_NOT_FOUND", code)
            assertEquals("Batch with id abc not found", message)
            assertTrue(details.isEmpty())
        }
    }

    @Test
    fun `platform exception with 422 maps correctly`() {
        val ex = PlatformException(
            code = "TICKET_LIMIT_EXCEEDED",
            message = "Max 3 open tickets allowed",
            httpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
        )

        val response = handler.handlePlatformException(ex)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("TICKET_LIMIT_EXCEEDED", response.body!!.error.code)
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    fun `validation failure returns 400 with field-level details`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        bindingResult.addError(FieldError("request", "email", "must not be blank"))
        bindingResult.addError(FieldError("request", "phone", "must be a valid phone number"))
        val ex = MethodArgumentNotValidException(null, bindingResult)

        val response = handler.handleValidation(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        with(response.body!!.error) {
            assertEquals("VALIDATION_ERROR", code)
            assertEquals(2, details.size)
            assertTrue(details.any { it.field == "email" && it.message == "must not be blank" })
            assertTrue(details.any { it.field == "phone" && it.message == "must be a valid phone number" })
        }
    }

    @Test
    fun `validation failure with missing message falls back to 'Invalid value'`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        bindingResult.addError(FieldError("request", "name", null, false, null, null, null))
        val ex = MethodArgumentNotValidException(null, bindingResult)

        val response = handler.handleValidation(ex)

        assertEquals("Invalid value", response.body!!.error.details.first().message)
    }

    // ── HttpMessageNotReadableException ──────────────────────────────────────

    @Test
    fun `malformed request body returns 400 with MALFORMED_REQUEST code`() {
        val ex = HttpMessageNotReadableException("bad json", MockHttpInputMessage(ByteArray(0)))

        val response = handler.handleUnreadableMessage(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("MALFORMED_REQUEST", response.body!!.error.code)
    }

    // ── Method not supported ─────────────────────────────────────────────────

    @Test
    fun `unsupported http method returns 405 with method name in message`() {
        val ex = HttpRequestMethodNotSupportedException("DELETE")

        val response = handler.handleMethodNotSupported(ex)

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.statusCode)
        with(response.body!!.error) {
            assertEquals("METHOD_NOT_ALLOWED", code)
            assertTrue(message.contains("DELETE"))
        }
    }

    // ── 404 ──────────────────────────────────────────────────────────────────

    @Test
    fun `unknown endpoint returns 404 with ENDPOINT_NOT_FOUND code`() {
        val ex = NoResourceFoundException(HttpMethod.GET, "/api/v1/nonexistent")

        val response = handler.handleNoResourceFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("ENDPOINT_NOT_FOUND", response.body!!.error.code)
    }

    // ── Catch-all ────────────────────────────────────────────────────────────

    @Test
    fun `unexpected exception returns 500 with generic message and no internal details`() {
        val ex = RuntimeException("NullPointerException at line 42 in SomeInternalClass")

        val response = handler.handleUnexpected(ex)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        with(response.body!!.error) {
            assertEquals("INTERNAL_ERROR", code)
            // Must not leak internal exception message to the client
            assertTrue(!message.contains("NullPointerException"))
            assertTrue(!message.contains("SomeInternalClass"))
        }
    }
}