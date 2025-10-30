-- 1. 重命名原表
ALTER TABLE log RENAME TO log_old;

-- 2. 删除旧索引（因为它们还绑定在 log_old 上）
DROP INDEX IF EXISTS log_create_time;
DROP INDEX IF EXISTS log_username;
DROP INDEX IF EXISTS log_ip;
DROP INDEX IF EXISTS log_type;

-- 3. 创建新表结构
CREATE TABLE log (
                     id integer,
                     public_id varchar(255) not null unique,
                     browser varchar(64),
                     create_time timestamp,
                     device_model varchar(64),
                     file_user_id varchar(24),
                     filepath TEXT,
                     ip varchar(40),
                     ip_info clob,
                     method varchar(10),
                     operating_system varchar(64),
                     operation_fun TEXT,
                     operation_module varchar(24),
                     remarks varchar(255),
                     show_name varchar(255),
                     status integer,
                     time bigint,
                     type varchar(16),
                     url varchar(255),
                     username varchar(255),
                     primary key (id)
);

-- 4. 创建新索引
CREATE INDEX log_create_time ON log (create_time);
CREATE INDEX log_username ON log (username);
CREATE INDEX log_ip ON log (ip);
CREATE INDEX log_type ON log (type);
CREATE INDEX log_file_user_id ON log (file_user_id);

-- 5. 复制数据
INSERT INTO log SELECT * FROM log_old;

-- 6. 删除旧表
DROP TABLE log_old;
