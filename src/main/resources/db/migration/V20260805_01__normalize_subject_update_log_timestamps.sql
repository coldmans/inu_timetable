-- subject_update_logs.applied_at was originally written as UTC to a timezone-less
-- TIMESTAMP column. From the 2026-07-31 Korea-time rollout onward it was written
-- as Asia/Seoul local time to the same column, so the historical values have two
-- different meanings.
--
-- The Korea-time writer was merged at 2026-07-31 00:54:37 Asia/Seoul. Values
-- below that local wall-clock boundary are the old UTC representation; values at
-- or above it are the new Asia/Seoul representation. Normalize both groups to a
-- real instant while converting the column to TIMESTAMP WITH TIME ZONE.
ALTER TABLE subject_update_logs
    ALTER COLUMN applied_at TYPE TIMESTAMP WITH TIME ZONE
    USING (
        CASE
            WHEN applied_at >= TIMESTAMP '2026-07-31 00:54:37'
                THEN applied_at AT TIME ZONE 'Asia/Seoul'
            ELSE applied_at AT TIME ZONE 'UTC'
        END
    );
