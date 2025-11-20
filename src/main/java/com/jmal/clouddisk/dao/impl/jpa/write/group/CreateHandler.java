package com.jmal.clouddisk.dao.impl.jpa.write.group;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.GroupRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.rbac.GroupDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("groupCreateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHandler implements IDataOperationHandler<GroupOperation.Create, GroupDO> {

    private final GroupRepository repo;

    @Override
    public GroupDO handle(GroupOperation.Create op) {
        return repo.save(op.entity());
    }
}
