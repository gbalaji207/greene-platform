package com.greene.content.service

import com.greene.content.domain.*
import com.greene.content.dto.*
import com.greene.content.repository.*
import com.greene.core.exception.PlatformException
import com.greene.core.storage.StorageService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class NodeServiceTest {

    @MockK lateinit var nodeRepository: ContentNodeRepository
    @MockK lateinit var itemDetailRepository: ContentItemDetailRepository
    @MockK lateinit var libraryRepository: ContentLibraryRepository
    @MockK lateinit var contentFileRepository: ContentFileRepository
    @MockK lateinit var entitlementRepository: ContentEntitlementRepository
    @MockK lateinit var storageService: StorageService

    @InjectMockKs
    lateinit var service: NodeService

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val libraryId = UUID.fromString("d1000000-0000-0000-0000-000000000001")
    private val nodeId    = UUID.fromString("e1000000-0000-0000-0000-000000000001")
    private val node2Id   = UUID.fromString("e1000000-0000-0000-0000-000000000002")
    private val callerId  = UUID.fromString("a1000000-0000-0000-0000-000000000001")
    private val now       = OffsetDateTime.now()

    private fun draftLibrary() = ContentLibrary(
        id = libraryId, name = "Sunflower Guide", status = LibraryStatus.DRAFT,
        createdBy = callerId, createdAt = now, updatedAt = now
    )

    private fun publishedLibrary() = ContentLibrary(
        id = libraryId, name = "Sunflower Guide", status = LibraryStatus.PUBLISHED,
        createdBy = callerId, createdAt = now, updatedAt = now
    )

    private fun archivedLibrary() = ContentLibrary(
        id = libraryId, name = "Sunflower Guide", status = LibraryStatus.ARCHIVED,
        createdBy = callerId, createdAt = now, updatedAt = now
    )

    private fun folderNode(id: UUID = nodeId, depth: Int = 1, parentId: UUID? = null) = ContentNode(
        id = id, libraryId = libraryId, parentId = parentId,
        nodeType = NodeType.FOLDER, title = "Week 1",
        sortOrder = 0, depth = depth, status = NodeStatus.PUBLISHED,
        createdAt = now, updatedAt = now
    )

    private fun itemNode(id: UUID = node2Id, depth: Int = 2, parentId: UUID? = nodeId) = ContentNode(
        id = id, libraryId = libraryId, parentId = parentId,
        nodeType = NodeType.ITEM, title = "Intro Video",
        sortOrder = 0, depth = depth, status = NodeStatus.DRAFT,
        createdAt = now, updatedAt = now
    )

    private fun videoDetail(nodeId: UUID = node2Id) = ContentItemDetail(
        nodeId = nodeId, itemType = ItemType.VIDEO,
        summary = "A short intro", durationSeconds = null,
        createdAt = now, updatedAt = now
    )

    // ── createNode ────────────────────────────────────────────────────────────

    @Test
    fun `createNode - FOLDER at root - depth 1, PUBLISHED status, no item detail saved`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        val slot = slot<ContentNode>()
        every { nodeRepository.save(capture(slot)) } answers { slot.captured }

        val result = service.createNode(libraryId, CreateNodeRequest(
            nodeType = NodeType.FOLDER, title = "Week 1"
        ))

        assertEquals(1,                     result.depth)
        assertEquals(NodeStatus.PUBLISHED,  result.status)
        assertEquals(NodeType.FOLDER,       result.nodeType)
        assertNull(result.parentId)
        assertNull(result.itemType)
        verify(exactly = 0) { itemDetailRepository.save(any()) }
    }

    @Test
    fun `createNode - ITEM VIDEO under FOLDER parent - depth 2, DRAFT status, item detail saved`() {
        val parent = folderNode()
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findById(nodeId) } returns Optional.of(parent)
        val nodeSlot = slot<ContentNode>()
        val detailSlot = slot<ContentItemDetail>()
        every { nodeRepository.save(capture(nodeSlot)) } answers { nodeSlot.captured }
        every { itemDetailRepository.save(capture(detailSlot)) } answers { detailSlot.captured }

        val result = service.createNode(libraryId, CreateNodeRequest(
            nodeType = NodeType.ITEM, title = "Intro Video",
            parentId = nodeId, itemType = ItemType.VIDEO, summary = "A short intro"
        ))

        assertEquals(2,                 result.depth)
        assertEquals(NodeStatus.DRAFT,  result.status)
        assertEquals(NodeType.ITEM,     result.nodeType)
        assertEquals(nodeId,            result.parentId)
        assertEquals(ItemType.VIDEO,    result.itemType)
        assertEquals("A short intro",   result.summary)
        verify(exactly = 1) { itemDetailRepository.save(any()) }
    }

    @Test
    fun `createNode - library not found - throws LIBRARY_NOT_FOUND 404`() {
        every { libraryRepository.findById(libraryId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            service.createNode(libraryId, CreateNodeRequest(nodeType = NodeType.FOLDER, title = "X"))
        }
        assertEquals("LIBRARY_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `createNode - library archived - throws LIBRARY_ARCHIVED 422`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(archivedLibrary())

        val ex = assertThrows<PlatformException> {
            service.createNode(libraryId, CreateNodeRequest(nodeType = NodeType.FOLDER, title = "X"))
        }
        assertEquals("LIBRARY_ARCHIVED", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
    }

    @Test
    fun `createNode - parent not found - throws NODE_NOT_FOUND 404`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findById(nodeId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            service.createNode(libraryId, CreateNodeRequest(
                nodeType = NodeType.FOLDER, title = "X", parentId = nodeId
            ))
        }
        assertEquals("NODE_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `createNode - parent is ITEM node - throws INVALID_PARENT_NODE 422`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findById(node2Id) } returns Optional.of(itemNode())

        val ex = assertThrows<PlatformException> {
            service.createNode(libraryId, CreateNodeRequest(
                nodeType = NodeType.ITEM, title = "X", parentId = node2Id, itemType = ItemType.ARTICLE
            ))
        }
        assertEquals("INVALID_PARENT_NODE", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
    }

    @Test
    fun `createNode - depth would exceed 3 - throws MAX_DEPTH_EXCEEDED 422`() {
        val deepParent = folderNode(depth = 3)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findById(nodeId) } returns Optional.of(deepParent)

        val ex = assertThrows<PlatformException> {
            service.createNode(libraryId, CreateNodeRequest(
                nodeType = NodeType.FOLDER, title = "X", parentId = nodeId
            ))
        }
        assertEquals("MAX_DEPTH_EXCEEDED", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
    }

    @Test
    fun `createNode - ITEM without itemType - throws VALIDATION_ERROR 400 with field detail`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())

        val ex = assertThrows<PlatformException> {
            service.createNode(libraryId, CreateNodeRequest(
                nodeType = NodeType.ITEM, title = "X"
            ))
        }
        assertEquals("VALIDATION_ERROR", ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
        assertTrue(ex.details.any { it.field == "itemType" })
    }

    // ── updateNode ────────────────────────────────────────────────────────────

    @Test
    fun `updateNode - title update on FOLDER - title saved, updatedAt refreshed`() {
        val folder = folderNode()
        every { nodeRepository.findById(nodeId) } returns Optional.of(folder)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { contentFileRepository.existsByNodeIdAndFileRole(nodeId, "PRIMARY") } returns false
        val slot = slot<ContentNode>()
        every { nodeRepository.save(capture(slot)) } answers { slot.captured }

        val result = service.updateNode(nodeId, UpdateNodeRequest(title = "Updated Title"))

        assertEquals("Updated Title", result.title)
        verify(exactly = 1) { nodeRepository.save(any()) }
    }

    @Test
    fun `updateNode - FOLDER receives only status - throws AT_LEAST_ONE_FIELD_REQUIRED 400`() {
        val folder = folderNode()
        every { nodeRepository.findById(nodeId) } returns Optional.of(folder)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())

        val ex = assertThrows<PlatformException> {
            service.updateNode(nodeId, UpdateNodeRequest(status = NodeStatus.PUBLISHED))
        }
        assertEquals("AT_LEAST_ONE_FIELD_REQUIRED", ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    @Test
    fun `updateNode - ITEM status update - status saved`() {
        val item = itemNode()
        val detail = videoDetail()
        every { nodeRepository.findById(node2Id) } returns Optional.of(item)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { itemDetailRepository.findByNodeId(node2Id) } returns detail
        every { contentFileRepository.existsByNodeIdAndFileRole(node2Id, "PRIMARY") } returns false
        val nodeSlot = slot<ContentNode>()
        every { nodeRepository.save(capture(nodeSlot)) } answers { nodeSlot.captured }
        every { itemDetailRepository.save(any()) } answers { firstArg() }

        val result = service.updateNode(node2Id, UpdateNodeRequest(status = NodeStatus.PUBLISHED))

        assertEquals(NodeStatus.PUBLISHED, result.status)
    }

    @Test
    fun `updateNode - node not found - throws NODE_NOT_FOUND 404`() {
        every { nodeRepository.findById(nodeId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> {
            service.updateNode(nodeId, UpdateNodeRequest(title = "X"))
        }
        assertEquals("NODE_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `updateNode - library archived - throws LIBRARY_ARCHIVED 422`() {
        every { nodeRepository.findById(nodeId) } returns Optional.of(folderNode())
        every { libraryRepository.findById(libraryId) } returns Optional.of(archivedLibrary())

        val ex = assertThrows<PlatformException> {
            service.updateNode(nodeId, UpdateNodeRequest(title = "X"))
        }
        assertEquals("LIBRARY_ARCHIVED", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
    }

    // ── moveNode ──────────────────────────────────────────────────────────────

    @Test
    fun `moveNode - move to new parent - depth and parentId updated`() {
        val newParentId = UUID.fromString("e1000000-0000-0000-0000-000000000099")
        val movingNode  = folderNode(depth = 1, parentId = null)
        val newParent   = folderNode(id = newParentId, depth = 1, parentId = null)
        every { nodeRepository.findById(nodeId) } returns Optional.of(movingNode)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findById(newParentId) } returns Optional.of(newParent)
        every { nodeRepository.findAllDescendantIds(nodeId) } returns listOf(nodeId)
        every { nodeRepository.findAllByIdIn(emptyList()) } returns emptyList()
        every { nodeRepository.save(any()) } answers { firstArg() }
        every { itemDetailRepository.findByNodeId(nodeId) } returns null
        every { contentFileRepository.existsByNodeIdAndFileRole(nodeId, "PRIMARY") } returns false

        val result = service.moveNode(nodeId, MoveNodeRequest(newParentId = newParentId, sortOrder = 0))

        assertEquals(newParentId, result.parentId)
        assertEquals(2, result.depth)
    }

    @Test
    fun `moveNode - move to root - depth 1, parentId null`() {
        val movingNode = itemNode(depth = 2, parentId = nodeId)
        every { nodeRepository.findById(node2Id) } returns Optional.of(movingNode)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findAllDescendantIds(node2Id) } returns listOf(node2Id)
        every { nodeRepository.findAllByIdIn(emptyList()) } returns emptyList()
        every { nodeRepository.save(any()) } answers { firstArg() }
        every { itemDetailRepository.findByNodeId(node2Id) } returns videoDetail()
        every { contentFileRepository.existsByNodeIdAndFileRole(node2Id, "PRIMARY") } returns false

        val result = service.moveNode(node2Id, MoveNodeRequest(newParentId = null))

        assertNull(result.parentId)
        assertEquals(1, result.depth)
    }

    @Test
    fun `moveNode - cross-library move - throws MOVE_CROSS_LIBRARY 422`() {
        val otherLibraryId = UUID.randomUUID()
        val newParentId    = UUID.randomUUID()
        val movingNode     = folderNode()
        val foreignParent  = ContentNode(
            id = newParentId, libraryId = otherLibraryId, parentId = null,
            nodeType = NodeType.FOLDER, title = "X",
            sortOrder = 0, depth = 1, status = NodeStatus.PUBLISHED,
            createdAt = now, updatedAt = now
        )
        every { nodeRepository.findById(nodeId) } returns Optional.of(movingNode)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findById(newParentId) } returns Optional.of(foreignParent)

        val ex = assertThrows<PlatformException> {
            service.moveNode(nodeId, MoveNodeRequest(newParentId = newParentId))
        }
        assertEquals("MOVE_CROSS_LIBRARY", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
    }

    @Test
    fun `moveNode - new parent is ITEM - throws INVALID_PARENT_NODE 422`() {
        val newParentId = node2Id
        val movingNode  = folderNode()
        val itemParent  = itemNode()
        every { nodeRepository.findById(nodeId) } returns Optional.of(movingNode)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findById(newParentId) } returns Optional.of(itemParent)

        val ex = assertThrows<PlatformException> {
            service.moveNode(nodeId, MoveNodeRequest(newParentId = newParentId))
        }
        assertEquals("INVALID_PARENT_NODE", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
    }

    @Test
    fun `moveNode - descendant would exceed depth 3 - throws MAX_DEPTH_EXCEEDED 422`() {
        val child2Id    = UUID.randomUUID()
        val newParentId = UUID.randomUUID()
        val movingNode  = folderNode(depth = 1)                // delta = +2
        val newParent   = folderNode(id = newParentId, depth = 2)
        val childAtD2   = folderNode(id = node2Id, depth = 2, parentId = nodeId)
        val grandChild  = ContentNode(
            id = child2Id, libraryId = libraryId, parentId = node2Id,
            nodeType = NodeType.FOLDER, title = "GC",
            sortOrder = 0, depth = 3, status = NodeStatus.PUBLISHED,
            createdAt = now, updatedAt = now
        )  // depth 3 + delta 2 = 5 > 3

        every { nodeRepository.findById(nodeId) } returns Optional.of(movingNode)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findById(newParentId) } returns Optional.of(newParent)
        every { nodeRepository.findAllDescendantIds(nodeId) } returns listOf(nodeId, node2Id, child2Id)
        every { nodeRepository.findAllByIdIn(listOf(node2Id, child2Id)) } returns listOf(childAtD2, grandChild)

        val ex = assertThrows<PlatformException> {
            service.moveNode(nodeId, MoveNodeRequest(newParentId = newParentId))
        }
        assertEquals("MAX_DEPTH_EXCEEDED", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
    }

    // ── reorderNodes ──────────────────────────────────────────────────────────

    @Test
    fun `reorderNodes - happy path - sortOrder assigned by array index`() {
        val idA = UUID.fromString("e1000000-0000-0000-0000-000000000010")
        val idB = UUID.fromString("e1000000-0000-0000-0000-000000000011")
        val nodeA = folderNode(id = idA, depth = 1)
        val nodeB = folderNode(id = idB, depth = 1)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findAllByIdIn(listOf(idA, idB)) } returns listOf(nodeA, nodeB)
        every { nodeRepository.saveAll(any<List<ContentNode>>()) } answers { firstArg() }

        service.reorderNodes(libraryId, ReorderNodesRequest(parentId = null, orderedNodeIds = listOf(idA, idB)))

        assertEquals(0, nodeA.sortOrder)
        assertEquals(1, nodeB.sortOrder)
        verify(exactly = 1) { nodeRepository.saveAll(any<List<ContentNode>>()) }
    }

    @Test
    fun `reorderNodes - node belongs to wrong parent - throws VALIDATION_ERROR 400`() {
        val wrongParentId = UUID.randomUUID()
        val nodeA = folderNode(depth = 1, parentId = wrongParentId)  // parentId != null (req expects null)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findAllByIdIn(listOf(nodeId)) } returns listOf(nodeA)

        val ex = assertThrows<PlatformException> {
            service.reorderNodes(libraryId, ReorderNodesRequest(parentId = null, orderedNodeIds = listOf(nodeId)))
        }
        assertEquals("VALIDATION_ERROR", ex.code)
        assertEquals(HttpStatus.BAD_REQUEST, ex.httpStatus)
    }

    // ── deleteNode ────────────────────────────────────────────────────────────

    @Test
    fun `deleteNode - happy path - items files nodes deleted storageService called per file`() {
        val fileKey = "uploads/file.mp4"
        val file    = ContentFile(nodeId = node2Id, fileKey = fileKey, mimeType = "video/mp4", sizeBytes = 1024L, fileRole = "PRIMARY")
        val folder  = folderNode()
        val item    = itemNode()

        every { nodeRepository.findById(nodeId) } returns Optional.of(folder)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findAllDescendantIds(nodeId) } returns listOf(nodeId, node2Id)
        every { contentFileRepository.findAllByNodeIdIn(listOf(nodeId, node2Id)) } returns listOf(file)
        every { storageService.delete(fileKey) } just Runs
        every { contentFileRepository.deleteAllByNodeIdIn(listOf(nodeId, node2Id)) } just Runs
        every { itemDetailRepository.deleteAllByNodeIdIn(listOf(nodeId, node2Id)) } just Runs
        every { nodeRepository.findAllByIdIn(listOf(nodeId, node2Id)) } returns listOf(folder, item)
        every { nodeRepository.delete(any()) } just Runs

        service.deleteNode(nodeId)

        verify(exactly = 1) { storageService.delete(fileKey) }
        verify(exactly = 1) { contentFileRepository.deleteAllByNodeIdIn(any()) }
        verify(exactly = 1) { itemDetailRepository.deleteAllByNodeIdIn(any()) }
        verify(exactly = 2) { nodeRepository.delete(any()) }
    }

    @Test
    fun `deleteNode - storage delete failure - logs WARN, does not throw, delete proceeds`() {
        val fileKey = "uploads/bad.mp4"
        val file    = ContentFile(nodeId = node2Id, fileKey = fileKey, mimeType = "video/mp4", sizeBytes = 1024L, fileRole = "PRIMARY")
        val folder  = folderNode()
        val item    = itemNode()

        every { nodeRepository.findById(nodeId) } returns Optional.of(folder)
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.findAllDescendantIds(nodeId) } returns listOf(nodeId, node2Id)
        every { contentFileRepository.findAllByNodeIdIn(any()) } returns listOf(file)
        every { storageService.delete(fileKey) } throws RuntimeException("S3 unreachable")
        every { contentFileRepository.deleteAllByNodeIdIn(any()) } just Runs
        every { itemDetailRepository.deleteAllByNodeIdIn(any()) } just Runs
        every { nodeRepository.findAllByIdIn(any()) } returns listOf(folder, item)
        every { nodeRepository.delete(any()) } just Runs

        // Must not throw
        assertDoesNotThrow { service.deleteNode(nodeId) }

        verify(exactly = 1) { contentFileRepository.deleteAllByNodeIdIn(any()) }
    }

    @Test
    fun `deleteNode - node not found - throws NODE_NOT_FOUND 404`() {
        every { nodeRepository.findById(nodeId) } returns Optional.empty()

        val ex = assertThrows<PlatformException> { service.deleteNode(nodeId) }
        assertEquals("NODE_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `deleteNode - library archived - throws LIBRARY_ARCHIVED 422`() {
        every { nodeRepository.findById(nodeId) } returns Optional.of(folderNode())
        every { libraryRepository.findById(libraryId) } returns Optional.of(archivedLibrary())

        val ex = assertThrows<PlatformException> { service.deleteNode(nodeId) }
        assertEquals("LIBRARY_ARCHIVED", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.httpStatus)
    }

    // ── getTree ───────────────────────────────────────────────────────────────

    @Test
    fun `getTree - staff - DRAFT and PUBLISHED items returned, status field present`() {
        val folder      = folderNode(depth = 1)
        val draftItem   = itemNode(depth = 2, parentId = nodeId)
        val detail      = videoDetail(node2Id)
        every { libraryRepository.findById(libraryId) } returns Optional.of(publishedLibrary())
        every { nodeRepository.fetchTreeByLibraryId(libraryId) } returns listOf(folder, draftItem)
        every { itemDetailRepository.findAllByNodeIdIn(any()) } returns listOf(detail)
        every { contentFileRepository.findNodeIdsWithPrimaryFileIn(any()) } returns emptyList()

        val result = service.getTree(libraryId, callerId, isClient = false)

        assertEquals(1, result.size)
        assertEquals(1, result[0].children.size)
        assertEquals(NodeStatus.PUBLISHED, result[0].status)         // folder status present
        assertEquals(NodeStatus.DRAFT,     result[0].children[0].status)  // item status present
    }

    @Test
    fun `getTree - CLIENT with entitlement - only PUBLISHED items returned, status null`() {
        val folder    = folderNode(depth = 1)
        val pubItem   = itemNode(depth = 2, parentId = nodeId).also { it.status = NodeStatus.PUBLISHED }
        val draftItem = ContentNode(
            id = UUID.randomUUID(), libraryId = libraryId, parentId = nodeId,
            nodeType = NodeType.ITEM, title = "Draft Item",
            sortOrder = 1, depth = 2, status = NodeStatus.DRAFT,
            createdAt = now, updatedAt = now
        )
        val detail = videoDetail(pubItem.id)
        every { libraryRepository.findById(libraryId) } returns Optional.of(publishedLibrary())
        every { entitlementRepository.existsByUserIdAndLibraryIdAndRevokedAtIsNull(callerId, libraryId) } returns true
        every { nodeRepository.fetchTreeByLibraryId(libraryId) } returns listOf(folder, pubItem, draftItem)
        every { itemDetailRepository.findAllByNodeIdIn(any()) } returns listOf(detail)
        every { contentFileRepository.findNodeIdsWithPrimaryFileIn(any()) } returns emptyList()

        val result = service.getTree(libraryId, callerId, isClient = true)

        assertEquals(1, result.size)
        assertEquals(1, result[0].children.size)       // only pubItem, draftItem filtered out
        assertNull(result[0].status)                   // status omitted for CLIENT
        assertNull(result[0].children[0].status)
    }

    @Test
    fun `getTree - CLIENT without entitlement - throws CONTENT_LOCKED 403`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(publishedLibrary())
        every { entitlementRepository.existsByUserIdAndLibraryIdAndRevokedAtIsNull(callerId, libraryId) } returns false

        val ex = assertThrows<PlatformException> { service.getTree(libraryId, callerId, isClient = true) }
        assertEquals("CONTENT_LOCKED", ex.code)
        assertEquals(HttpStatus.FORBIDDEN, ex.httpStatus)
    }

    @Test
    fun `getTree - CLIENT on DRAFT library - throws LIBRARY_NOT_FOUND 404`() {
        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())

        val ex = assertThrows<PlatformException> { service.getTree(libraryId, callerId, isClient = true) }
        assertEquals("LIBRARY_NOT_FOUND", ex.code)
        assertEquals(HttpStatus.NOT_FOUND, ex.httpStatus)
    }

    @Test
    fun `getTree - tree structure - nested children assembled correctly from flat CTE result`() {
        val childId      = UUID.fromString("e1000000-0000-0000-0000-000000000003")
        val grandChildId = UUID.fromString("e1000000-0000-0000-0000-000000000004")
        val root  = folderNode(id = nodeId,      depth = 1, parentId = null)
        val child = folderNode(id = childId,     depth = 2, parentId = nodeId)
        val grand = folderNode(id = grandChildId,depth = 3, parentId = childId)

        every { libraryRepository.findById(libraryId) } returns Optional.of(draftLibrary())
        every { nodeRepository.fetchTreeByLibraryId(libraryId) } returns listOf(root, child, grand)
        every { itemDetailRepository.findAllByNodeIdIn(any()) } returns emptyList()
        every { contentFileRepository.findNodeIdsWithPrimaryFileIn(any()) } returns emptyList()

        val result = service.getTree(libraryId, callerId, isClient = false)

        assertEquals(1, result.size)
        assertEquals(nodeId,       result[0].id)
        assertEquals(1,            result[0].children.size)
        assertEquals(childId,      result[0].children[0].id)
        assertEquals(1,            result[0].children[0].children.size)
        assertEquals(grandChildId, result[0].children[0].children[0].id)
        assertTrue(result[0].children[0].children[0].children.isEmpty())
    }
}


