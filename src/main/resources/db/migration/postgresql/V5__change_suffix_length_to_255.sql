-- 修改 files 表的 suffix 字段长度
ALTER TABLE files
ALTER COLUMN suffix TYPE varchar(255);

-- 修改 trash 表的 suffix 字段长度
ALTER TABLE trash
ALTER COLUMN suffix TYPE varchar(255);
