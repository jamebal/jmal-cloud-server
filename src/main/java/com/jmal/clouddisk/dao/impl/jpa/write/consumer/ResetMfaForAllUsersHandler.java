package com.jmal.clouddisk.dao.impl.jpa.write.consumer;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.UserRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("resetMfaForAllUsersHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ResetMfaForAllUsersHandler implements IDataOperationHandler<UserOperation.ResetMfaForAllUsers, Void> {

    private final UserRepository repo;

    @Override
    public Void handle(UserOperation.ResetMfaForAllUsers op) {
        repo.resetMfaForAllUsers();
        return null;
    }
}
