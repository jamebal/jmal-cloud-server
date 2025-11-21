package com.jmal.clouddisk.dao.impl.jpa.write.group;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.GroupRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("groupCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<GroupOperation.CreateAll, Void> {

    private final GroupRepository repo;

    @Override
    public Void handle(GroupOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
