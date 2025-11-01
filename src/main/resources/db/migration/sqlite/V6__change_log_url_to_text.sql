-- 1. 创建新表
CREATE TABLE log_new (
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
                         url TEXT,
                         username varchar(255),
                         primary key (id)
);

-- 2. 拷贝数据
INSERT INTO log_new SELECT * FROM log;

-- 3. 删除原表
DROP TABLE log;

-- 4. 重命名新表
ALTER TABLE log_new RENAME TO log;

-- 5. 重建索引
CREATE INDEX log_create_time ON log (create_time);
CREATE INDEX log_username ON log (username);
CREATE INDEX log_ip ON log (ip);
CREATE INDEX log_type ON log (type);
CREATE INDEX log_file_user_id ON log (file_user_id);
