package com.greene.content.service

import com.greene.content.domain.ContentLibrary
import com.greene.content.domain.LibraryStatus
import com.greene.content.dto.CreateLibraryRequest
import com.greene.content.dto.UpdateLibraryRequest
import com.greene.content.repository.ContentLibraryRepository
import com.greene.core.exception.PlatformException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class LibraryServiceTest {

    @MockK
    private lateinit var libraryRepository: ContentLibraryRepository

    @InjectMockKs
    private lateinit var libraryService: LibraryService

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val callerId  = UUID.fromString("a1000000-0000-0000-0000-000000000001")
    private val libraryId = UUID.fromString("d1000000-0000-0000-0000-000000000001")
    private val now       = OffsetDateTime.now()

    private fun buildLibrary(
        status: LibraryStatus = LibraryStatus.DRAFT,
        name: String = "Sunflower Guide",
        description: String? = null,
    ) = ContentLibrary(
        id          = libraryId,
        name        = name,
        description = description,
        status      = status,
        createdBy   = callerId,
        createdAt   = now,
        updatedAt   = now,
    )

    // ── createLibrary ─────────────────────────────────────────────────────────

    @Test
    fun `createLibrary - happy path - saves entity with DRAFT status and callerId, returns response`() {
        val slot = slot<ContentLibrary>()
        every { libraryRepository.save(capture(slot)) } answers { slot.captured }

        val result = libraryService.createLibrary(
            CreateLibraryRequest(name = "Sunflower Guide"),
            callerId
        )

        assertEquals("Sunflower Guide",    result.name)
        assertEquals(LibraryStatus.DRAFT,  result.status)
        assertEquals(callerId,             result.createdBy)
        assertNull(result.description)
        verify(exactly = 1) { libraryRepository.save(match {
            it.name == "Sunflower Guide" && it.status == LibraryStatus.DRAFT && it.createdBy == callerId
        }) }
    }

    @Test
    fun `createLibrary - description null - description is null in saved entity`() {
        val slot = slot<ContentLibrary>()
        every { libraryRepository.save(capture(slot)) } answers { slot.captured }

        libraryService.createLibrary(CreateLibraryRequest(name = "Guide", description = null), callerId)

        assertNull(slot.captured.description)
    }

    // ── getLibrary ────────────────────────────────────────────────────────────

    @Test
    fun `getLibrary - library exists - returns mapped response`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(buildLibrary())

        val result = libraryService.getLibrary(libraryId)

        assertEquals(libraryId,           result.id)
        assertEquals("Sunflower Guide",   result.name)
        assertEquals(LibraryStatus.DRAFT, result.status)
        assertEquals(callerId,            result.createdBy)
    }

    @Test
    fun `getLibrary - not found - throws LIBRARY_NOT_FOUND 404`() {
        every { libraryRepository.findById(libraryId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            libraryService.getLibrary(libraryId)
        }

        assertEquals("LIBRARY_NOT_FOUND",  ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
        assertEquals("Library not found",  ex.message)
    }

    // ── updateLibrary - happy paths ───────────────────────────────────────────

    @Test
    fun `updateLibrary - name update - name changes, updatedAt refreshed, other fields unchanged`() {
        val original = buildLibrary(description = "Some desc")
        val slot     = slot<ContentLibrary>()
        every { libraryRepository.findById(libraryId) } returns Optional.of(original)
        every { libraryRepository.save(capture(slot)) } answers { slot.captured }

        val result = libraryService.updateLibrary(libraryId, UpdateLibraryRequest(name = "New Name"))

        assertEquals("New Name",          result.name)
        assertEquals("Some desc",         result.description)
        assertEquals(LibraryStatus.DRAFT, result.status)
        verify(exactly = 1) { libraryRepository.save(match { it.name == "New Name" }) }
    }

    @Test
    fun `updateLibrary - description clear with empty string - description set to null in saved entity`() {
        val original = buildLibrary(description = "Old desc")
        val slot     = slot<ContentLibrary>()
        every { libraryRepository.findById(libraryId) } returns Optional.of(original)
        every { libraryRepository.save(capture(slot)) } answers { slot.captured }

        val result = libraryService.updateLibrary(libraryId, UpdateLibraryRequest(description = ""))

        assertNull(result.description)
        assertNull(slot.captured.description)
    }

    @Test
    fun `updateLibrary - DRAFT to PUBLISHED - status updated, no exception`() {
        val original = buildLibrary(status = LibraryStatus.DRAFT)
        val slot     = slot<ContentLibrary>()
        every { libraryRepository.findById(libraryId) } returns Optional.of(original)
        every { libraryRepository.save(capture(slot)) } answers { slot.captured }

        val result = libraryService.updateLibrary(libraryId, UpdateLibraryRequest(status = LibraryStatus.PUBLISHED))

        assertEquals(LibraryStatus.PUBLISHED, result.status)
        assertEquals(LibraryStatus.PUBLISHED, slot.captured.status)
    }

    @Test
    fun `updateLibrary - PUBLISHED to DRAFT - status updated`() {
        val original = buildLibrary(status = LibraryStatus.PUBLISHED)
        val slot     = slot<ContentLibrary>()
        every { libraryRepository.findById(libraryId) } returns Optional.of(original)
        every { libraryRepository.save(capture(slot)) } answers { slot.captured }

        val result = libraryService.updateLibrary(libraryId, UpdateLibraryRequest(status = LibraryStatus.DRAFT))

        assertEquals(LibraryStatus.DRAFT, result.status)
    }

    @Test
    fun `updateLibrary - PUBLISHED to ARCHIVED - status updated`() {
        val original = buildLibrary(status = LibraryStatus.PUBLISHED)
        val slot     = slot<ContentLibrary>()
        every { libraryRepository.findById(libraryId) } returns Optional.of(original)
        every { libraryRepository.save(capture(slot)) } answers { slot.captured }

        val result = libraryService.updateLibrary(libraryId, UpdateLibraryRequest(status = LibraryStatus.ARCHIVED))

        assertEquals(LibraryStatus.ARCHIVED, result.status)
    }

    // ── updateLibrary - error paths ───────────────────────────────────────────

    @Test
    fun `updateLibrary - empty body - throws AT_LEAST_ONE_FIELD_REQUIRED 400`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(buildLibrary())

        val ex = assertThrows<PlatformException> {
            libraryService.updateLibrary(libraryId, UpdateLibraryRequest())
        }

        assertEquals("AT_LEAST_ONE_FIELD_REQUIRED", ex.code)
        assertEquals(HttpStatus.BAD_REQUEST,         ex.httpStatus)
        assertEquals("At least one field must be provided", ex.message)
    }

    @Test
    fun `updateLibrary - library not found - throws LIBRARY_NOT_FOUND 404`() {
        every { libraryRepository.findById(libraryId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            libraryService.updateLibrary(libraryId, UpdateLibraryRequest(name = "X"))
        }

        assertEquals("LIBRARY_NOT_FOUND",  ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `updateLibrary - ARCHIVED library - throws LIBRARY_ARCHIVED 422 before any field check`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(buildLibrary(status = LibraryStatus.ARCHIVED))

        val ex = assertThrows<PlatformException> {
            libraryService.updateLibrary(libraryId, UpdateLibraryRequest(name = "X"))
        }

        assertEquals("LIBRARY_ARCHIVED",                    ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,       ex.httpStatus)
        assertEquals("Archived libraries cannot be modified", ex.message)
    }

    @Test
    fun `updateLibrary - DRAFT to ARCHIVED - throws INVALID_STATUS_TRANSITION 422`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(buildLibrary(status = LibraryStatus.DRAFT))

        val ex = assertThrows<PlatformException> {
            libraryService.updateLibrary(libraryId, UpdateLibraryRequest(status = LibraryStatus.ARCHIVED))
        }

        assertEquals("INVALID_STATUS_TRANSITION",     ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
        assertEquals("Invalid status transition",     ex.message)
    }

    @Test
    fun `updateLibrary - PUBLISHED to PUBLISHED - throws INVALID_STATUS_TRANSITION 422`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(buildLibrary(status = LibraryStatus.PUBLISHED))

        val ex = assertThrows<PlatformException> {
            libraryService.updateLibrary(libraryId, UpdateLibraryRequest(status = LibraryStatus.PUBLISHED))
        }

        assertEquals("INVALID_STATUS_TRANSITION",     ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
    }

    // ── listLibraries ─────────────────────────────────────────────────────────

    @Test
    fun `listLibraries - no status filter - calls findAll with null spec, returns PagedData`() {
        val library = buildLibrary()
        val pageResult = org.springframework.data.domain.PageImpl(listOf(library))
        every { libraryRepository.findAll(null, any<org.springframework.data.domain.Pageable>()) } returns pageResult

        val result = libraryService.listLibraries(null, 1, 20)

        assertEquals(1,         result.items.size)
        assertEquals(libraryId, result.items[0].id)
        assertEquals(1,         result.page)
        assertEquals(20,        result.pageSize)
        assertEquals(1L,        result.total)
    }

    @Test
    fun `listLibraries - with status filter - spec includes status predicate, only matching items returned`() {
        val library = buildLibrary(status = LibraryStatus.PUBLISHED)
        val pageResult = org.springframework.data.domain.PageImpl(listOf(library))
        every { libraryRepository.findAll(any<org.springframework.data.jpa.domain.Specification<ContentLibrary>>(), any<org.springframework.data.domain.Pageable>()) } returns pageResult

        val result = libraryService.listLibraries(LibraryStatus.PUBLISHED, 1, 10)

        assertEquals(1,                        result.items.size)
        assertEquals(LibraryStatus.PUBLISHED,  result.items[0].status)
        assertEquals(10,                       result.pageSize)
    }
}

