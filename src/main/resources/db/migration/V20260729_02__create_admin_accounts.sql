CREATE TABLE IF NOT EXISTS admin_accounts (
    id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    password_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    credential_version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT admin_accounts_pk PRIMARY KEY (id),
    CONSTRAINT admin_accounts_singleton_ck CHECK (id = 1),
    CONSTRAINT admin_accounts_username_uk UNIQUE (username),
    CONSTRAINT admin_accounts_credential_version_ck CHECK (credential_version >= 1)
);

ALTER TABLE session_migration_tokens
    ADD COLUMN IF NOT EXISTS admin_credential_version BIGINT;
