-- V20260728_01 is reserved for the user inquiries migration.
CREATE TABLE IF NOT EXISTS schedule_room_segments (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    room VARCHAR(100) NOT NULL,
    start_time DOUBLE PRECISION NOT NULL,
    end_time DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_schedule_room_segments_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules(id) ON DELETE CASCADE,
    CONSTRAINT uq_schedule_room_segment
        UNIQUE (schedule_id, room, start_time, end_time),
    CONSTRAINT chk_schedule_room_segment_time
        CHECK (start_time < end_time)
);

CREATE INDEX IF NOT EXISTS idx_schedule_room_segments_schedule_id
    ON schedule_room_segments (schedule_id);
