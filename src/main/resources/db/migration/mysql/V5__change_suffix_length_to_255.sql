-- 修改 files 表的 suffix 字段长度
ALTER TABLE files
    MODIFY COLUMN suffix varchar(255);

-- 修改 trash 表的 suffix 字段长度
ALTER TABLE trash
    MODIFY COLUMN suffix varchar(255);
