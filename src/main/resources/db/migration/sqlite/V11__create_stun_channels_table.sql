CREATE TABLE IF NOT EXISTS stun_channels (
    id VARCHAR(24) PRIMARY KEY,
    channel_id VARCHAR(128) NOT NULL UNIQUE,
    addr VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS stun_channels_channel_id ON stun_channels(channel_id);
