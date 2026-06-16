CREATE UNIQUE INDEX IF NOT EXISTS uk_user_major_department
    ON user_majors (user_id, department);
