package com.jmal.clouddisk.dao.impl.jpa.write.stunchannel;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.StunChannelRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.stun.StunChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("stunChannelUpsertHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpsertHandler implements IDataOperationHandler<StunChannelOperation.Upsert, Void> {

    private final StunChannelRepository repository;

    @Override
    public Void handle(StunChannelOperation.Upsert operation) {
        StunChannel stunChannel = repository.findByChannelId(operation.channelId()).orElseGet(StunChannel::new);
        stunChannel.setChannelId(operation.channelId());
        stunChannel.setAddr(operation.addr());
        repository.save(stunChannel);
        return null;
    }
}
