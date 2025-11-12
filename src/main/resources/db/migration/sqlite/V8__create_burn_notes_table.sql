-- Description: 创建阅后即焚笔记表

CREATE TABLE burn_notes (
                            id VARCHAR(24) NOT NULL PRIMARY KEY,
                            user_id VARCHAR(24) NOT NULL,
                            encrypted_content TEXT NOT NULL,
                            is_file BOOLEAN NOT NULL DEFAULT 0,
                            total_chunks INTEGER DEFAULT NULL,
                            file_size BIGINT DEFAULT NULL,
                            views_left INTEGER DEFAULT NULL,
                            expire_at DATETIME DEFAULT NULL,
                            created_time timestamp NOT NULL,
                            updated_time timestamp NOT NULL
);

-- 创建索引
CREATE INDEX idx_burn_notes_user_id ON burn_notes(user_id);
CREATE INDEX idx_burn_notes_expire_at ON burn_notes(expire_at);
CREATE INDEX idx_burn_notes_created_time ON burn_notes(created_time);
