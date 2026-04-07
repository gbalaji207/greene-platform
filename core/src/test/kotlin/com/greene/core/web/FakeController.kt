package com.greene.core.web

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.greene.core.exception.PlatformException
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/test")
class FakeController {

    data class FakeRequest(
        @field:NotBlank val name: String?,
        @field:Email val email: String?,
    )

    @GetMapping("/platform-exception")
    fun throwPlatform(): Nothing = throw PlatformException(
        code = "BATCH_NOT_FOUND",
        message = "Batch not found",
        httpStatus = HttpStatus.NOT_FOUND,
    )

    @GetMapping("/unexpected")
    fun throwUnexpected(): Nothing = throw RuntimeException("internal details that must not leak")

    @PostMapping("/validate", consumes = ["application/json"])
    fun validate(@Valid @RequestBody body: FakeRequest): String = "ok"
}

data class FakeRequest @JsonCreator constructor(
    @JsonProperty("name") @field:NotBlank val name: String?,
    @JsonProperty("email") @field:Email val email: String?,
)