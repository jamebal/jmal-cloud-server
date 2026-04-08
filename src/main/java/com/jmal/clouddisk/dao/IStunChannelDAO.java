package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.stun.StunChannel;

import java.util.Optional;

public interface IStunChannelDAO {

    void upsert(String channelId, String addr);

    Optional<StunChannel> findByChannelId(String channelId);
}
