package com.jmal.clouddisk.dao.impl.jpa.write.role;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.RoleRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("roleRemoveByIdInHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByIdInHandler implements IDataOperationHandler<RoleOperation.removeByIdIn, Void> {

    private final RoleRepository repo;

    @Override
    public Void handle(RoleOperation.removeByIdIn op) {
        repo.removeByIdIn(op.roleIdList());
        return null;
    }
}
