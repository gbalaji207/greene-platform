package com.greene.core.web

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(controllers = [FakeController::class])
@Import(GlobalExceptionHandler::class, TestSecurityConfig::class)
class GlobalExceptionHandlerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    @WithMockUser
    fun `platform exception serialises to correct error envelope`() {
        mockMvc.get("/test/platform-exception")
            .andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_JSON) }
                jsonPath("$.error.code") { value("BATCH_NOT_FOUND") }
                jsonPath("$.error.message") { value("Batch not found") }
                jsonPath("$.error.details") { isArray() }
                jsonPath("$.data") { doesNotExist() }
            }
    }

    @Test
    @WithMockUser
    fun `bean validation failure serialises field errors correctly`() {
        mockMvc.post("/test/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "", "email": "not-an-email"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.details") { isArray() }
            jsonPath("$.error.details.length()") { value(2) }
        }
    }

    @Test
    @WithMockUser
    fun `malformed json body returns 400 MALFORMED_REQUEST`() {
        mockMvc.post("/test/validate") {
            contentType = MediaType.APPLICATION_JSON
            content = """{ this is not json }"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("MALFORMED_REQUEST") }
        }
    }

    @Test
    @WithMockUser
    fun `post to get-only endpoint returns 405`() {
        mockMvc.post("/test/platform-exception") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.error.code") { value("METHOD_NOT_ALLOWED") }
        }
    }

    @Test
    @WithMockUser
    fun `unknown endpoint returns 404 ENDPOINT_NOT_FOUND`() {
        mockMvc.get("/test/does-not-exist")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("ENDPOINT_NOT_FOUND") }
            }
    }

    @Test
    @WithMockUser
    fun `unexpected exception returns 500 and does not leak internal message`() {
        mockMvc.get("/test/unexpected")
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.error.code") { value("INTERNAL_ERROR") }
                jsonPath("$.error.message") { value("An unexpected error occurred") }
            }
    }
}