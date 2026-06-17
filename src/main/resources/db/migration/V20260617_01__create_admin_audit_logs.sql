CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_username VARCHAR(100),
    method VARCHAR(12) NOT NULL,
    path VARCHAR(255) NOT NULL,
    status INTEGER NOT NULL,
    success BOOLEAN NOT NULL,
    client_ip VARCHAR(64),
    user_agent VARCHAR(255),
    error_type VARCHAR(120),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_created_at
    ON admin_audit_logs (created_at);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_path
    ON admin_audit_logs (path);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_admin_username
    ON admin_audit_logs (admin_username);
