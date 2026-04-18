package com.greene.content.service

import com.greene.content.domain.*
import com.greene.content.dto.*
import com.greene.content.repository.*
import com.greene.core.api.error.ErrorDetail
import com.greene.core.exception.PlatformException
import com.greene.core.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Transactional
class NodeService(
    private val nodeRepository: ContentNodeRepository,
    private val itemDetailRepository: ContentItemDetailRepository,
    private val libraryRepository: ContentLibraryRepository,
    private val contentFileRepository: ContentFileRepository,
    private val entitlementRepository: ContentEntitlementRepository,
    private val storageService: StorageService
) {

    private val log = LoggerFactory.getLogger(NodeService::class.java)

    // -------------------------------------------------------------------------
    // createNode
    // -------------------------------------------------------------------------

    fun createNode(libraryId: UUID, req: CreateNodeRequest): NodeResponse {
        // 1. Load library
        val library = loadLibrary(libraryId)

        // 2. Library must not be archived
        assertNotArchived(library)

        // 3. Resolve parent and depth
        val depth: Int
        if (req.parentId != null) {
            val parent = nodeRepository.findById(req.parentId)
                .orElseThrow { PlatformException("NODE_NOT_FOUND", "Node not found", HttpStatus.NOT_FOUND) }
            if (parent.libraryId != libraryId) {
                throw PlatformException("INVALID_PARENT_NODE", "Parent node does not belong to this library", HttpStatus.UNPROCESSABLE_ENTITY)
            }
            if (parent.nodeType == NodeType.ITEM) {
                throw PlatformException("INVALID_PARENT_NODE", "An ITEM node cannot be a parent", HttpStatus.UNPROCESSABLE_ENTITY)
            }
            depth = parent.depth + 1
            if (depth > 3) {
                throw PlatformException("MAX_DEPTH_EXCEEDED", "Maximum tree depth of 3 exceeded", HttpStatus.UNPROCESSABLE_ENTITY)
            }
        } else {
            depth = 1
        }

        // 4 & 5. Cross-field validation: ITEM requires itemType
        if (req.nodeType == NodeType.ITEM && req.itemType == null) {
            throw PlatformException(
                "VALIDATION_ERROR",
                "Validation failed",
                HttpStatus.BAD_REQUEST,
                listOf(ErrorDetail("itemType", "itemType is required when nodeType is ITEM"))
            )
        }

        // 6. Build and save ContentNode
        val node = nodeRepository.save(
            ContentNode(
                libraryId = libraryId,
                parentId = req.parentId,
                nodeType = req.nodeType!!,
                title = req.title!!,
                sortOrder = req.sortOrder ?: 0,
                depth = depth,
                status = if (req.nodeType == NodeType.FOLDER) NodeStatus.PUBLISHED else NodeStatus.DRAFT
            )
        )

        // 7. Build and save ContentItemDetail for ITEM nodes
        val itemDetail: ContentItemDetail? = if (node.nodeType == NodeType.ITEM) {
            itemDetailRepository.save(
                ContentItemDetail(
                    nodeId = node.id,
                    itemType = req.itemType!!,
                    summary = req.summary
                )
            )
        } else null

        // 8. Return response
        return toNodeResponse(node, itemDetail, hasFile = false)
    }

    // -------------------------------------------------------------------------
    // updateNode
    // -------------------------------------------------------------------------

    fun updateNode(nodeId: UUID, req: UpdateNodeRequest): NodeResponse {
        // 1. Load node
        val node = nodeRepository.findById(nodeId)
            .orElseThrow { PlatformException("NODE_NOT_FOUND", "Node not found", HttpStatus.NOT_FOUND) }

        // 2. Load library and assert not archived
        val library = loadLibrary(node.libraryId)
        assertNotArchived(library)

        // 3. Determine actionable fields by nodeType / itemType
        val itemDetail: ContentItemDetail? = if (node.nodeType == NodeType.ITEM) {
            itemDetailRepository.findByNodeId(nodeId)
        } else null

        val titleActionable = true
        val summaryActionable = node.nodeType == NodeType.ITEM
        val statusActionable = node.nodeType == NodeType.ITEM
        val durationActionable = node.nodeType == NodeType.ITEM && itemDetail?.itemType == ItemType.VIDEO

        // 4. At least one actionable field must be non-null in the request
        val anyActionable =
            (titleActionable && req.title != null) ||
            (summaryActionable && req.summary != null) ||
            (statusActionable && req.status != null) ||
            (durationActionable && req.durationSeconds != null)

        if (!anyActionable) {
            throw PlatformException("AT_LEAST_ONE_FIELD_REQUIRED", "At least one field must be provided", HttpStatus.BAD_REQUEST)
        }

        // 5. Apply updates
        if (req.title != null) node.title = req.title
        if (req.summary != null && summaryActionable) itemDetail!!.summary = req.summary
        if (req.status != null && statusActionable) node.status = req.status
        if (req.durationSeconds != null && durationActionable) itemDetail!!.durationSeconds = req.durationSeconds

        // 6. Persist
        node.updatedAt = OffsetDateTime.now()
        nodeRepository.save(node)
        if (itemDetail != null) itemDetailRepository.save(itemDetail)

        // 7. hasFile check
        val filePresent = hasFile(nodeId)

        // 8. Return response
        return toNodeResponse(node, itemDetail, filePresent)
    }

    // -------------------------------------------------------------------------
    // moveNode
    // -------------------------------------------------------------------------

    fun moveNode(nodeId: UUID, req: MoveNodeRequest): NodeResponse {
        // 1. Load node
        val node = nodeRepository.findById(nodeId)
            .orElseThrow { PlatformException("NODE_NOT_FOUND", "Node not found", HttpStatus.NOT_FOUND) }

        // 2. Load library and assert not archived
        val library = loadLibrary(node.libraryId)
        assertNotArchived(library)

        // 3 & 4. Resolve new depth
        val newDepth: Int
        if (req.newParentId != null) {
            val newParent = nodeRepository.findById(req.newParentId)
                .orElseThrow { PlatformException("NODE_NOT_FOUND", "Node not found", HttpStatus.NOT_FOUND) }
            if (newParent.libraryId != node.libraryId) {
                throw PlatformException("MOVE_CROSS_LIBRARY", "Cannot move node to a different library", HttpStatus.UNPROCESSABLE_ENTITY)
            }
            if (newParent.nodeType == NodeType.ITEM) {
                throw PlatformException("INVALID_PARENT_NODE", "An ITEM node cannot be a parent", HttpStatus.UNPROCESSABLE_ENTITY)
            }
            newDepth = newParent.depth + 1
        } else {
            newDepth = 1
        }

        // 5. depthDelta
        val depthDelta = newDepth - node.depth

        // 6. Load all descendant IDs and check max depth
        val allDescendantIds = nodeRepository.findAllDescendantIds(nodeId)
        val descendants = nodeRepository.findAllByIdIn(allDescendantIds.filter { it != nodeId })
        if (descendants.any { it.depth + depthDelta > 3 }) {
            throw PlatformException("MAX_DEPTH_EXCEEDED", "Maximum tree depth of 3 exceeded", HttpStatus.UNPROCESSABLE_ENTITY)
        }

        // 7. Update node
        node.parentId = req.newParentId
        node.sortOrder = req.sortOrder ?: 0
        node.depth = newDepth
        node.updatedAt = OffsetDateTime.now()
        nodeRepository.save(node)

        // 8. Update all descendants
        val now = OffsetDateTime.now()
        descendants.forEach { d ->
            d.depth += depthDelta
            d.updatedAt = now
        }
        if (descendants.isNotEmpty()) nodeRepository.saveAll(descendants)

        val itemDetail = if (node.nodeType == NodeType.ITEM) itemDetailRepository.findByNodeId(nodeId) else null
        return toNodeResponse(node, itemDetail, hasFile(nodeId))
    }

    // -------------------------------------------------------------------------
    // reorderNodes
    // -------------------------------------------------------------------------

    fun reorderNodes(libraryId: UUID, req: ReorderNodesRequest) {
        // 1. Load library
        loadLibrary(libraryId)

        // 2. Load nodes by IDs
        val nodeIds = req.orderedNodeIds!!
        val nodes = nodeRepository.findAllByIdIn(nodeIds)

        // 3. Validate each node
        val nodesById = nodes.associateBy { it.id }
        for (id in nodeIds) {
            val n = nodesById[id]
                ?: throw PlatformException("VALIDATION_ERROR", "Node $id not found in library", HttpStatus.BAD_REQUEST)
            if (n.libraryId != libraryId) {
                throw PlatformException("VALIDATION_ERROR", "Node $id does not belong to this library", HttpStatus.BAD_REQUEST)
            }
            if (n.parentId != req.parentId) {
                throw PlatformException("VALIDATION_ERROR", "Node $id does not have the expected parentId", HttpStatus.BAD_REQUEST)
            }
        }

        // 4. Assign sortOrder by position
        val now = OffsetDateTime.now()
        nodeIds.forEachIndexed { i, id ->
            val n = nodesById[id]!!
            n.sortOrder = i
            n.updatedAt = now
        }

        // 5. Batch save
        nodeRepository.saveAll(nodes)
    }

    // -------------------------------------------------------------------------
    // deleteNode
    // -------------------------------------------------------------------------

    fun deleteNode(nodeId: UUID) {
        // 1. Load node
        val node = nodeRepository.findById(nodeId)
            .orElseThrow { PlatformException("NODE_NOT_FOUND", "Node not found", HttpStatus.NOT_FOUND) }

        // 2. Load library and assert not archived
        val library = loadLibrary(node.libraryId)
        assertNotArchived(library)

        // 3. Collect all descendant IDs (including node itself)
        val allIds = nodeRepository.findAllDescendantIds(nodeId)

        // 4. Load all content_files rows for affected nodes
        val files = contentFileRepository.findAllByNodeIdIn(allIds)

        // 5. Best-effort storage deletion
        files.forEach { f ->
            try {
                storageService.delete(f.fileKey)
            } catch (ex: Exception) {
                log.warn("Failed to delete storage object '${f.fileKey}': ${ex.message}")
            }
        }

        // 6. Delete content_files rows
        contentFileRepository.deleteAllByNodeIdIn(allIds)

        // 7. Delete content_item_details rows
        itemDetailRepository.deleteAllByNodeIdIn(allIds)

        // 8. Delete content_nodes leaf-to-root (deepest first)
        val allNodes = nodeRepository.findAllByIdIn(allIds)
        val sortedByDepthDesc = allNodes.sortedByDescending { it.depth }
        sortedByDepthDesc.forEach { nodeRepository.delete(it) }
    }

    // -------------------------------------------------------------------------
    // getTree
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun getTree(libraryId: UUID, callerId: UUID, isClient: Boolean): List<TreeNodeResponse> {
        // 1. Load library
        val library = loadLibrary(libraryId)

        // 2. Client-specific checks
        if (isClient) {
            if (library.status == LibraryStatus.DRAFT || library.status == LibraryStatus.ARCHIVED) {
                throw PlatformException("LIBRARY_NOT_FOUND", "Library not found", HttpStatus.NOT_FOUND)
            }
            if (!entitlementRepository.existsByUserIdAndLibraryIdAndRevokedAtIsNull(callerId, libraryId)) {
                throw PlatformException("CONTENT_LOCKED", "You are not entitled to access this library", HttpStatus.FORBIDDEN)
            }
        }

        // 3. Load all nodes via recursive CTE
        val allNodes = nodeRepository.fetchTreeByLibraryId(libraryId)

        // 4. Bulk-load item details → map by nodeId
        val nodeIds = allNodes.map { it.id }
        val detailMap: Map<UUID, ContentItemDetail> =
            itemDetailRepository.findAllByNodeIdIn(nodeIds).associateBy { it.nodeId }

        // 5. Bulk-load node IDs that have a PRIMARY file
        val fileNodeIds: Set<UUID> =
            contentFileRepository.findNodeIdsWithPrimaryFileIn(nodeIds).toHashSet()

        // 6. Filter for CLIENT: always keep FOLDER, only PUBLISHED ITEMs
        val visibleNodes = if (isClient) {
            allNodes.filter { n ->
                n.nodeType == NodeType.FOLDER || n.status == NodeStatus.PUBLISHED
            }
        } else {
            allNodes
        }

        // 7. Build nested tree in memory
        val visibleIds = visibleNodes.map { it.id }.toHashSet()
        val byParent: Map<UUID?, List<ContentNode>> = visibleNodes.groupBy { it.parentId }

        fun buildChildren(parentId: UUID?): List<TreeNodeResponse> =
            (byParent[parentId] ?: emptyList()).map { n ->
                val detail = detailMap[n.id]
                TreeNodeResponse(
                    id = n.id,
                    nodeType = n.nodeType,
                    title = n.title,
                    sortOrder = n.sortOrder,
                    depth = n.depth,
                    status = if (isClient) null else n.status,
                    itemType = detail?.itemType,
                    summary = detail?.summary,
                    durationSeconds = detail?.durationSeconds,
                    hasFile = if (n.nodeType == NodeType.ITEM) fileNodeIds.contains(n.id) else null,
                    children = buildChildren(n.id)
                )
            }

        // 8. Return root-level list
        return buildChildren(null)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun loadLibrary(libraryId: UUID): ContentLibrary =
        libraryRepository.findById(libraryId)
            .orElseThrow { PlatformException("LIBRARY_NOT_FOUND", "Library not found", HttpStatus.NOT_FOUND) }

    private fun assertNotArchived(library: ContentLibrary) {
        if (library.status == LibraryStatus.ARCHIVED) {
            throw PlatformException("LIBRARY_ARCHIVED", "Archived libraries cannot be modified", HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    private fun hasFile(nodeId: UUID): Boolean =
        contentFileRepository.existsByNodeIdAndFileRole(nodeId, "PRIMARY")

    private fun toNodeResponse(node: ContentNode, detail: ContentItemDetail?, hasFile: Boolean): NodeResponse =
        NodeResponse(
            id = node.id,
            libraryId = node.libraryId,
            parentId = node.parentId,
            nodeType = node.nodeType,
            title = node.title,
            sortOrder = node.sortOrder,
            depth = node.depth,
            status = node.status,
            itemType = detail?.itemType,
            summary = detail?.summary,
            durationSeconds = detail?.durationSeconds,
            hasFile = hasFile,
            createdAt = node.createdAt,
            updatedAt = node.updatedAt
        )
}

