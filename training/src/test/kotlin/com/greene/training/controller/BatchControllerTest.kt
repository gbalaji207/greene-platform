package com.greene.training.controller

import com.greene.core.auth.security.JwtAccessDeniedHandler
import com.greene.core.auth.security.JwtAuthenticationEntryPoint
import com.greene.core.auth.security.JwtAuthenticationFilter
import com.greene.core.config.SecurityConfig
import com.greene.core.exception.PlatformException
import com.greene.core.web.GlobalExceptionHandler
import com.greene.training.domain.BatchStatus
import com.greene.training.dto.BatchResponse
import com.greene.training.service.BatchService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * @WebMvcTest slice for [BatchController].
 *
 * Imports the real [SecurityConfig] (not TestSecurityConfig) so that:
 *  - [JwtAuthenticationEntryPoint] produces the correct 401 JSON envelope (SC-10, SC-14).
 *  - [JwtAuthenticationFilter] is present but skips JWT processing when
 *    [SecurityContextHolder] already contains a test-supplied authentication.
 *  - [GlobalExceptionHandler.handleAccessDenied] produces the 403 envelope (SC-11, SC-15).
 *  - CSRF is disabled (SecurityConfig.csrf { it.disable() }) so POST works without a token.
 *
 * Principal note:
 *   [BatchController.createBatch] casts `authentication.principal` to `String` to extract the
 *   caller UUID.  `@WithMockUser` sets a `UserDetails` principal — casting would fail.
 *   Tests that reach the controller body therefore use [authentication] post-processor with
 *   a [UsernamePasswordAuthenticationToken] whose principal is a UUID string.
 *   Tests that never reach the body (validation errors, 403, 401) safely use `@WithMockUser`.
 */
@WebMvcTest(BatchController::class)
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    JwtAuthenticationEntryPoint::class,
    JwtAccessDeniedHandler::class,
)
class BatchControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var batchService: BatchService

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val callerId  = UUID.fromString("f7e6d5c4-b3a2-1098-fedc-ba9876543210")
    private val batchId   = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private val startDate = LocalDate.of(2026, 5, 1)

    /**
     * Authentication token with a String principal — required for POST tests that reach
     * the controller body and call UUID.fromString(authentication.principal as String).
     */
    private fun adminAuth() = UsernamePasswordAuthenticationToken(
        callerId.toString(),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    private fun stubBatchResponse(status: BatchStatus = BatchStatus.OPEN) = BatchResponse(
        id             = batchId,
        name           = "Batch April 2026",
        description    = null,
        startDate      = startDate,
        endDate        = null,
        location       = null,
        topics         = null,
        maxSeats       = null,
        status         = status,
        trainingStatus = null,
        createdBy      = callerId,
        createdAt      = OffsetDateTime.now(),
        updatedAt      = OffsetDateTime.now(),
    )

    private val validPostBody = """
        {
          "name": "Batch April 2026",
          "startDate": "2026-05-01",
          "status": "OPEN"
        }
    """.trimIndent()

    // ── POST /api/v1/batches ──────────────────────────────────────────────────

    /**
     * Happy path — valid body, ADMIN auth.
     * Uses adminAuth() (String principal) because the controller body extracts callerId.
     */
    @Test
    fun `POST createBatch - 201 with valid request and ADMIN auth`() {
        every { batchService.createBatch(any(), callerId) } returns stubBatchResponse()

        mockMvc.post("/api/v1/batches") {
            with(authentication(adminAuth()))
            contentType = MediaType.APPLICATION_JSON
            content     = validPostBody
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.id")      { value(batchId.toString()) }
            jsonPath("$.data.name")    { value("Batch April 2026") }
            jsonPath("$.data.status")  { value("OPEN") }
            jsonPath("$.meta.version") { value("1.0") }
        }
    }

    /**
     * Bean validation fires during argument resolution — before the method body runs.
     * @WithMockUser is safe here: the String principal cast is never reached.
     */
    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `POST createBatch - 400 when name is blank`() {
        mockMvc.post("/api/v1/batches") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"name": "", "startDate": "2026-05-01"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")              { value("VALIDATION_ERROR") }
            jsonPath("$.error.details[0].field")  { value("name") }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `POST createBatch - 400 when startDate is missing`() {
        mockMvc.post("/api/v1/batches") {
            contentType = MediaType.APPLICATION_JSON
            content     = """{"name": "Batch April 2026"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")              { value("VALIDATION_ERROR") }
            jsonPath("$.error.details[0].field")  { value("startDate") }
        }
    }

    /**
     * Service layer rejects CLOSED status — the controller body IS reached, so we need
     * a String principal via adminAuth().
     * "CLOSED" is a valid BatchStatus enum value so Jackson deserialises it without error;
     * the rejection happens inside BatchService.
     */
    @Test
    fun `POST createBatch - 400 INVALID_BATCH_STATUS when service rejects CLOSED`() {
        every { batchService.createBatch(any(), any()) } throws PlatformException(
            code       = "INVALID_BATCH_STATUS",
            message    = "Batch can only be created with status DRAFT or OPEN",
            httpStatus = HttpStatus.BAD_REQUEST,
        )

        mockMvc.post("/api/v1/batches") {
            with(authentication(adminAuth()))
            contentType = MediaType.APPLICATION_JSON
            content     = """{"name": "Batch April 2026", "startDate": "2026-05-01", "status": "CLOSED"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")    { value("INVALID_BATCH_STATUS") }
            jsonPath("$.error.message") { value("Batch can only be created with status DRAFT or OPEN") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    /**
     * No authentication at all — JwtAuthenticationEntryPoint writes 401 JSON directly.
     */
    @Test
    fun `POST createBatch - 401 when no JWT provided`() {
        mockMvc.post("/api/v1/batches") {
            contentType = MediaType.APPLICATION_JSON
            content     = validPostBody
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("UNAUTHORIZED") }
        }
    }

    /**
     * CLIENT role — @PreAuthorize fires before the method body; AccessDeniedException
     * propagates to GlobalExceptionHandler.handleAccessDenied() → 403.
     * @WithMockUser is safe here: the principal cast is never reached.
     */
    @Test
    @WithMockUser(roles = ["CLIENT"])
    fun `POST createBatch - 403 when caller has CLIENT role`() {
        mockMvc.post("/api/v1/batches") {
            contentType = MediaType.APPLICATION_JSON
            content     = validPostBody
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
    }

    // ── GET /api/v1/batches/{id} ──────────────────────────────────────────────

    /**
     * GET does not extract a principal from the SecurityContext — @WithMockUser is safe.
     */
    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET getBatch - 200 with valid UUID returns batch`() {
        every { batchService.getBatch(batchId) } returns stubBatchResponse()

        mockMvc.get("/api/v1/batches/$batchId").andExpect {
            status { isOk() }
            jsonPath("$.data.id")      { value(batchId.toString()) }
            jsonPath("$.data.status")  { value("OPEN") }
            jsonPath("$.meta.version") { value("1.0") }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET getBatch - 404 BATCH_NOT_FOUND when batch does not exist`() {
        every { batchService.getBatch(batchId) } throws PlatformException(
            code       = "BATCH_NOT_FOUND",
            message    = "Batch not found",
            httpStatus = HttpStatus.NOT_FOUND,
        )

        mockMvc.get("/api/v1/batches/$batchId").andExpect {
            status { isNotFound() }
            jsonPath("$.error.code")    { value("BATCH_NOT_FOUND") }
            jsonPath("$.error.message") { value("Batch not found") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    /**
     * Type conversion of "not-a-uuid" → UUID fails before the method body — @WithMockUser safe.
     * GlobalExceptionHandler.handleMethodArgumentTypeMismatch() produces the 400 detail.
     */
    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `GET getBatch - 400 when path variable is not a valid UUID`() {
        mockMvc.get("/api/v1/batches/not-a-uuid").andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code")               { value("VALIDATION_ERROR") }
            jsonPath("$.error.details[0].field")   { value("id") }
            jsonPath("$.error.details[0].message") { value("invalid UUID format") }
        }
    }
}

