CREATE TABLE IF NOT EXISTS stun_channels (
    id VARCHAR(24) NOT NULL,
    channel_id VARCHAR(128) NOT NULL,
    addr VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY stun_channels_channel_id (channel_id)
);
