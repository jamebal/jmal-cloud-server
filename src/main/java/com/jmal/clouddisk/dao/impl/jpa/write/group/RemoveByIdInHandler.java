package com.jmal.clouddisk.dao.impl.jpa.write.group;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.GroupRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("groupRemoveByIdInHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByIdInHandler implements IDataOperationHandler<GroupOperation.RemoveByIdIn, Void> {

    private final GroupRepository repo;

    @Override
    public Void handle(GroupOperation.RemoveByIdIn op) {
        repo.deleteAllByIdInBatch(op.groupIds());
        return null;
    }
}
