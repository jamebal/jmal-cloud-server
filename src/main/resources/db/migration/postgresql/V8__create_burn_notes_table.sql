-- PostgreSQL Flyway Migration
-- Description: 创建阅后即焚笔记表

CREATE TABLE burn_notes (
                            id VARCHAR(24) NOT NULL,
                            user_id VARCHAR(24) NOT NULL,
                            encrypted_content TEXT NOT NULL,
                            is_file BOOLEAN NOT NULL DEFAULT FALSE,
                            total_chunks INTEGER DEFAULT NULL,
                            file_size BIGINT DEFAULT NULL,
                            views_left INTEGER DEFAULT NULL,
                            expire_at TIMESTAMP(6) DEFAULT NULL,
                            created_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT pk_burn_notes PRIMARY KEY (id)
);

-- 创建索引
CREATE INDEX idx_burn_notes_user_id ON burn_notes(user_id);
CREATE INDEX idx_burn_notes_expire_at ON burn_notes(expire_at);
CREATE INDEX idx_burn_notes_created_time ON burn_notes(created_time);

-- 添加表注释
COMMENT ON TABLE burn_notes IS '阅后即焚笔记表';
COMMENT ON COLUMN burn_notes.id IS '笔记ID(主键)';
COMMENT ON COLUMN burn_notes.user_id IS '创建者用户ID';
COMMENT ON COLUMN burn_notes.encrypted_content IS '加密后的内容';
COMMENT ON COLUMN burn_notes.meta IS '元数据(JSON格式)';
COMMENT ON COLUMN burn_notes.is_file IS '是否为文件类型';
COMMENT ON COLUMN burn_notes.total_chunks IS '文件分片总数';
COMMENT ON COLUMN burn_notes.file_size IS '文件原始大小(字节)';
COMMENT ON COLUMN burn_notes.views_left IS '剩余查看次数';
COMMENT ON COLUMN burn_notes.expire_at IS '过期时间';
COMMENT ON COLUMN burn_notes.created_time IS '创建时间';
COMMENT ON COLUMN burn_notes.updated_time IS '更新时间';
