-- V7__create_content_tables.sql

-- content_libraries
CREATE TABLE content_libraries (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- content_nodes
CREATE TABLE content_nodes (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    library_id  UUID        NOT NULL REFERENCES content_libraries(id),
    parent_id   UUID        REFERENCES content_nodes(id),
    node_type   VARCHAR(10) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    depth       INT         NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- content_item_details
CREATE TABLE content_item_details (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    node_id          UUID        NOT NULL UNIQUE REFERENCES content_nodes(id),
    item_type        VARCHAR(10) NOT NULL,
    summary          VARCHAR(500),
    duration_seconds INT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- content_files
CREATE TABLE content_files (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    node_id    UUID         NOT NULL REFERENCES content_nodes(id),
    file_key   VARCHAR(500) NOT NULL,
    mime_type  VARCHAR(100) NOT NULL,
    size_bytes BIGINT       NOT NULL,
    file_role  VARCHAR(20)  NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- content_entitlements
CREATE TABLE content_entitlements (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID        NOT NULL,
    library_id UUID        NOT NULL REFERENCES content_libraries(id),
    granted_by UUID        NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_entitlement_user_library UNIQUE (user_id, library_id)
);

-- Indexes: content_nodes
CREATE INDEX idx_content_nodes_library_id
    ON content_nodes(library_id);

CREATE INDEX idx_content_nodes_parent_id
    ON content_nodes(parent_id);

CREATE INDEX idx_content_nodes_library_parent_sort
    ON content_nodes(library_id, parent_id, sort_order);

-- Indexes: content_files
CREATE INDEX idx_content_files_node_id
    ON content_files(node_id);

CREATE INDEX idx_content_files_node_role
    ON content_files(node_id, file_role);

-- Indexes: content_entitlements
CREATE INDEX idx_content_entitlements_user_library
    ON content_entitlements(user_id, library_id);

CREATE INDEX idx_content_entitlements_library_id
    ON content_entitlements(library_id);

