CREATE TABLE IF NOT EXISTS spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS spring_session_ix1 ON spring_session (session_id);
CREATE INDEX IF NOT EXISTS spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX IF NOT EXISTS spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE IF NOT EXISTS spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session(primary_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS session_migration_tokens (
    token_hash CHAR(64) NOT NULL,
    principal_type VARCHAR(16) NOT NULL,
    user_id BIGINT,
    admin_username VARCHAR(100),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_migration_tokens_pk PRIMARY KEY (token_hash),
    CONSTRAINT session_migration_tokens_principal_type_ck
        CHECK (principal_type IN ('USER', 'ADMIN')),
    CONSTRAINT session_migration_tokens_principal_ck
        CHECK (
            (principal_type = 'USER' AND user_id IS NOT NULL AND admin_username IS NULL)
            OR
            (principal_type = 'ADMIN' AND user_id IS NULL AND admin_username IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS session_migration_tokens_expiry_ix
    ON session_migration_tokens (expires_at);

CREATE TABLE IF NOT EXISTS login_rate_limits (
    attempt_key CHAR(64) NOT NULL,
    failed_count INT NOT NULL,
    blocked_until TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT login_rate_limits_pk PRIMARY KEY (attempt_key),
    CONSTRAINT login_rate_limits_failed_count_ck CHECK (failed_count >= 0)
);

CREATE INDEX IF NOT EXISTS login_rate_limits_updated_at_ix
    ON login_rate_limits (updated_at);

CREATE TABLE IF NOT EXISTS shared_cache_versions (
    cache_scope VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT shared_cache_versions_pk PRIMARY KEY (cache_scope)
);

INSERT INTO shared_cache_versions (cache_scope, version)
VALUES ('subject-all', 0), ('subject-filters', 0)
ON CONFLICT (cache_scope) DO NOTHING;
