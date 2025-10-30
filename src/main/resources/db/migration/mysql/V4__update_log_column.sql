ALTER TABLE log MODIFY COLUMN operation_fun LONGTEXT;
ALTER TABLE log MODIFY COLUMN filepath LONGTEXT;

-- 为 log 表的 file_user_id 列添加索引
CREATE INDEX log_file_user_id ON log (file_user_id);
