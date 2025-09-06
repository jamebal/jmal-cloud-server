package com.jmal.clouddisk.dao.impl.jpa.write.role;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.RoleRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("roleCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<RoleOperation.CreateAll, Void> {

    private final RoleRepository repo;

    @Override
    public Void handle(RoleOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
