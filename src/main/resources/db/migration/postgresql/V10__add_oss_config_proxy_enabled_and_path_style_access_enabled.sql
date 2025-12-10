-- 在oss_config表中添加proxy_enabled和path_style_access_enabled字段，默认值均为0（false）
ALTER TABLE oss_config ADD COLUMN proxy_enabled BOOLEAN;
ALTER TABLE oss_config ADD COLUMN path_style_access_enabled BOOLEAN;
