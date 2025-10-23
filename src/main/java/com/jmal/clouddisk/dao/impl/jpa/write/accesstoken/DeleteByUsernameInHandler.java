package com.jmal.clouddisk.dao.impl.jpa.write.accesstoken;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.AccessTokenRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteByUsernameInHandler implements IDataOperationHandler<AccessTokenOperation.DeleteByUsernameIn, Void> {

    private final AccessTokenRepository repo;

    @Override
    public Void handle(AccessTokenOperation.DeleteByUsernameIn operation) {
        repo.deleteByUsernameIn(operation.usernames());
        return null;
    }
}
