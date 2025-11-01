-- 1. files 表
PRAGMA foreign_keys=off;

CREATE TABLE files_new (
                           id integer,
                           public_id varchar(255) not null unique,
                           content_type varchar(128),
                           del_tag integer,
                           etag varchar(64),
                           etag_update_failed_attempts integer,
                           has_content boolean not null,
                           has_content_text boolean not null,
                           has_html boolean not null,
                           is_favorite boolean,
                           is_folder boolean,
                           last_etag_update_error TEXT,
                           last_etag_update_request_at timestamp,
                           lucene_index integer,
                           md5 varchar(255),
                           mount_file_id varchar(255),
                           name varchar(255) not null,
                           needs_etag_update boolean,
                           oss_folder varchar(255),
                           path varchar(255) not null,
                           size bigint,
                           suffix varchar(255),
                           update_date timestamp not null,
                           upload_date timestamp not null,
                           user_id varchar(24) not null,
                           props_id bigint not null unique,
                           children_count integer,
                           retry_at timestamp,
                           primary key (id)
);

INSERT INTO files_new SELECT * FROM files;

DROP TABLE files;

ALTER TABLE files_new RENAME TO files;

-- 重新创建索引
CREATE INDEX files_name ON files (name);
CREATE INDEX files_size ON files (size);
CREATE INDEX files_is_folder ON files (is_folder);
CREATE INDEX files_update_date ON files (update_date);
CREATE INDEX files_upload_date ON files (upload_date);
CREATE INDEX files_user_id ON files (user_id);
CREATE INDEX files_path ON files (path);
CREATE INDEX files_mount_file_id ON files (mount_file_id);
CREATE INDEX files_del_tag ON files (del_tag);

-- 2. trash 表
CREATE TABLE trash_new (
                           id integer,
                           public_id varchar(255) not null unique,
                           content_type varchar(128),
                           has_content boolean,
                           hidden boolean,
                           is_folder boolean,
                           md5 varchar(255),
                           move boolean,
                           name varchar(255),
                           path varchar(255),
                           props clob,
                           size bigint,
                           suffix varchar(255),
                           update_date timestamp,
                           upload_date timestamp,
                           user_id varchar(24),
                           primary key (id)
);

INSERT INTO trash_new SELECT * FROM trash;

DROP TABLE trash;

ALTER TABLE trash_new RENAME TO trash;

-- 重新创建索引
CREATE INDEX trash_name ON trash (name);
CREATE INDEX trash_update_date ON trash (update_date);
CREATE INDEX trash_upload_date ON trash (upload_date);
CREATE INDEX trash_trash_user_id ON trash (user_id);
CREATE INDEX trash_path ON trash (path);

PRAGMA foreign_keys=on;
