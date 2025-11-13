-- Description: 创建阅后即焚笔记表

CREATE TABLE `burn_notes` (
                              `id` VARCHAR(24) NOT NULL COMMENT '笔记ID(主键)',
                              `user_id` VARCHAR(24) NOT NULL COMMENT '创建者用户ID',
                              `encrypted_content` MEDIUMTEXT NOT NULL COMMENT '加密后的内容',
                              `is_file` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为文件类型',
                              `total_chunks` INT DEFAULT NULL COMMENT '文件分片总数',
                              `file_size` BIGINT DEFAULT NULL COMMENT '文件原始大小(字节)',
                              `views_left` INT DEFAULT NULL COMMENT '剩余查看次数',
                              `expire_at` DATETIME(6) DEFAULT NULL COMMENT '过期时间',
                              `created_time` DATETIME(6) NOT NULL COMMENT '创建时间',
                              `updated_time` DATETIME(6) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expire_at` (`expire_at`),
    KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阅后即焚笔记表';
