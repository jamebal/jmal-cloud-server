package com.jmal.clouddisk.dao.impl.jpa.write.stunchannel;

import com.jmal.clouddisk.model.stun.StunChannel;

public final class StunChannelOperation {

    private StunChannelOperation() {
    }

    public record CreateAll(Iterable<StunChannel> entities) implements IStunChannelOperation<Void> {
    }

    public record Upsert(String channelId, String addr) implements IStunChannelOperation<Void> {
    }
}
