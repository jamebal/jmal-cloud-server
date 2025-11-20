-- 创建 user_groups 表
CREATE TABLE `user_groups` (
                               `id` varchar(24) NOT NULL,
                               `created_time` datetime,
                               `updated_time` datetime,
                               `code` varchar(32) NOT NULL,
                               `name` varchar(64) NOT NULL,
                               `description` text,
                               `roles` text,
                               PRIMARY KEY (`id`)
);

-- 创建唯一索引
CREATE UNIQUE INDEX `uk_user_groups_code` ON `user_groups` (`code`);

-- 2. 在 consumers 表中添加 groups 字段
-- SQLite 的 ALTER TABLE 支持添加列
ALTER TABLE `consumers` ADD COLUMN `groups` text;
