ALTER TABLE subjects
    ADD COLUMN IF NOT EXISTS syllabus_available BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS subject_import_plans (
    id VARCHAR(36) PRIMARY KEY,
    semester VARCHAR(20) NOT NULL,
    source_format VARCHAR(32) NOT NULL,
    source_filename VARCHAR(255),
    source_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    deactivate_missing BOOLEAN NOT NULL DEFAULT FALSE,
    total_rows INTEGER NOT NULL,
    unchanged_count INTEGER NOT NULL,
    plan_payload TEXT NOT NULL,
    apply_result TEXT,
    created_at TIMESTAMP NOT NULL,
    applied_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subject_import_plans_created_at
    ON subject_import_plans (created_at DESC);

-- 검토 원본과 반영 결과에는 관리자용 변경 내역이 담기므로 Data API에서는 노출하지 않는다.
ALTER TABLE subject_import_plans ENABLE ROW LEVEL SECURITY;
