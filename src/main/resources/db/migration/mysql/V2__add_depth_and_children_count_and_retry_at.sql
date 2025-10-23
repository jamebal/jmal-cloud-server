ALTER TABLE files
    ADD COLUMN children_count INT COMMENT '仅文件夹有效, 子文件/文件夹数量',
    ADD COLUMN retry_at timestamp COMMENT '计算etag的重试时间';
