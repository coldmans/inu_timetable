CREATE TABLE IF NOT EXISTS user_majors (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    major_type VARCHAR(20) NOT NULL,
    department VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_majors_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_major_type_department
    ON user_majors (user_id, major_type, department);

INSERT INTO user_majors (user_id, major_type, department, created_at)
SELECT id, 'PRIMARY', major, COALESCE(created_at, CURRENT_TIMESTAMP)
FROM users
WHERE major IS NOT NULL
  AND major <> ''
ON CONFLICT DO NOTHING;
