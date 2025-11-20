-- 创建 user_groups 表
CREATE TABLE `user_groups` (
                               `id` varchar(24) NOT NULL COMMENT 'ID',
                               `created_time` datetime(6) DEFAULT NULL COMMENT '创建时间',
                               `updated_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
                               `code` varchar(32) NOT NULL COMMENT '组标识',
                               `name` varchar(64) NOT NULL COMMENT '组名',
                               `description` text COMMENT '描述',
                               `roles` json DEFAULT NULL COMMENT '角色ID列表',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_user_groups_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组表';

-- 在 consumers 表中添加 groups 字段
ALTER TABLE `consumers` ADD COLUMN `groups` json DEFAULT NULL COMMENT '用户组ID列表';
