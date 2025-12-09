-- 在oss_config表中添加proxy_enabled和path_style_access_enabled字段
ALTER TABLE oss_config ADD COLUMN proxy_enabled bit(1);
ALTER TABLE oss_config ADD COLUMN path_style_access_enabled bit(1);
