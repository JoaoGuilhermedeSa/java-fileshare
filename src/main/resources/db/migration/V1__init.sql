CREATE TABLE file_metadata (
    id               UUID        PRIMARY KEY,
    original_name    VARCHAR(512) NOT NULL,
    stored_path      VARCHAR(512) NOT NULL UNIQUE,
    virtual_directory VARCHAR(512),
    content_type     VARCHAR(255) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    checksum         VARCHAR(64)  NOT NULL,   -- SHA-256 hex of original plaintext
    encryption_iv    VARCHAR(32)  NOT NULL,   -- AES-GCM IV, hex-encoded (12 bytes = 24 hex chars)
    uploaded_at      TIMESTAMPTZ  NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE file_tags (
    id      UUID         PRIMARY KEY,
    file_id UUID         NOT NULL REFERENCES file_metadata(id) ON DELETE CASCADE,
    name    VARCHAR(255) NOT NULL,
    value   VARCHAR(512) NOT NULL
);

-- Indexes for common search patterns
CREATE INDEX idx_file_metadata_original_name    ON file_metadata(original_name);
CREATE INDEX idx_file_metadata_virtual_directory ON file_metadata(virtual_directory);
CREATE INDEX idx_file_metadata_uploaded_at       ON file_metadata(uploaded_at);
CREATE INDEX idx_file_tags_file_id               ON file_tags(file_id);
CREATE INDEX idx_file_tags_name_value            ON file_tags(name, value);
