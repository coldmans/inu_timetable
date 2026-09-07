-- 문의 작성 전에 사용자에게 보여줄 관리자 작성 Q&A.
-- 초기 Q&A는 넣지 않으며 관리자가 문의 관리 화면에서 직접 등록한다.
CREATE TABLE IF NOT EXISTS inquiry_faqs (
    id BIGSERIAL PRIMARY KEY,
    question VARCHAR(200) NOT NULL,
    answer VARCHAR(2000) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_inquiry_faqs_active_order
    ON inquiry_faqs (active, sort_order, id);
