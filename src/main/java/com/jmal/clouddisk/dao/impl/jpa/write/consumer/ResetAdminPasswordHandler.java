package com.jmal.clouddisk.dao.impl.jpa.write.consumer;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.UserRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("ResetAdminPasswordHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ResetAdminPasswordHandler implements IDataOperationHandler<UserOperation.ResetAdminPassword, Boolean> {

    private final UserRepository repo;

    @Override
    public Boolean handle(UserOperation.ResetAdminPassword op) {
        int updated = repo.updatePasswordByCreatorTrue(op.password());
        return updated > 0;
    }
}
