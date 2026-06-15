CREATE TABLE IF NOT EXISTS user_activity_daily (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_date DATE NOT NULL,
    first_seen_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_activity_daily_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_user_activity_daily_user_date
        UNIQUE (user_id, activity_date)
);

CREATE INDEX IF NOT EXISTS idx_user_activity_daily_date
    ON user_activity_daily (activity_date);

CREATE INDEX IF NOT EXISTS idx_user_activity_daily_user
    ON user_activity_daily (user_id);
