package com.jmal.clouddisk.dao.impl.jpa.write.role;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.RoleRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("roleCreateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHandler implements IDataOperationHandler<RoleOperation.Create, Void> {

    private final RoleRepository repo;

    @Override
    public Void handle(RoleOperation.Create op) {
        repo.save(op.entity());
        return null;
    }
}
