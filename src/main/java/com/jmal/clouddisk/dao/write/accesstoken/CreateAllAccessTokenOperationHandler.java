package com.jmal.clouddisk.dao.write.accesstoken;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.AccessTokenRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllAccessTokenOperationHandler implements IDataOperationHandler<AccessTokenOperation.CreateAll, Void> {

    private final AccessTokenRepository repo;

    @Override
    public Void handle(AccessTokenOperation.CreateAll operation) {
        repo.saveAll(operation.entities());
        return null;
    }
}
