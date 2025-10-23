package com.jmal.clouddisk.dao.impl.jpa.write.tag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TagRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("tagRemoveByIdInHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByIdInHandler implements IDataOperationHandler<TagOperation.RemoveByIdIn, Void> {

    private final TagRepository repo;

    @Override
    public Void handle(TagOperation.RemoveByIdIn op) {
        repo.removeByIdIn(op.tagIdList());
        return null;
    }
}
