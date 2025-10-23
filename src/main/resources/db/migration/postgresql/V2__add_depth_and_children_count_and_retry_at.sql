ALTER TABLE files
    ADD COLUMN children_count integer; -- 仅文件夹有效, 子文件/文件夹数量
ALTER TABLE files
    ADD COLUMN retry_at timestamp; -- 计算etag的重试时间
