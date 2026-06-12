ALTER TABLE subjects
    ADD COLUMN IF NOT EXISTS course_code VARCHAR(32);

ALTER TABLE subjects
    ADD COLUMN IF NOT EXISTS semester VARCHAR(20);

ALTER TABLE subjects
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_subjects_semester_course_code
    ON subjects (semester, course_code)
    WHERE semester IS NOT NULL AND course_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subjects_course_code
    ON subjects (course_code);

CREATE INDEX IF NOT EXISTS idx_subjects_semester_active
    ON subjects (semester, active);
