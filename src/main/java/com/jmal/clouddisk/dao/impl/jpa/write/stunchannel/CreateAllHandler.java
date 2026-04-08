package com.jmal.clouddisk.dao.impl.jpa.write.stunchannel;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.StunChannelRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("stunChannelCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<StunChannelOperation.CreateAll, Void> {

    private final StunChannelRepository repository;

    @Override
    public Void handle(StunChannelOperation.CreateAll operation) {
        repository.saveAll(operation.entities());
        return null;
    }
}
