ALTER TABLE log ALTER COLUMN operation_fun TYPE TEXT;
ALTER TABLE log ALTER COLUMN filepath TYPE TEXT;

-- 为 log 表的 file_user_id 列添加索引
CREATE INDEX log_file_user_id ON log (file_user_id);
