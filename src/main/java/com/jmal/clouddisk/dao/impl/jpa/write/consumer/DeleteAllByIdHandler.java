package com.jmal.clouddisk.dao.impl.jpa.write.consumer;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.UserRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("consumerDeleteAllByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteAllByIdHandler implements IDataOperationHandler<UserOperation.DeleteAllById, Void> {

    private final UserRepository repo;

    @Override
    public Void handle(UserOperation.DeleteAllById op) {
        repo.deleteAllById(op.idList());
        return null;
    }
}
