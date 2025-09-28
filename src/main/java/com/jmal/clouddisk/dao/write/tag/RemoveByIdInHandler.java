package com.jmal.clouddisk.dao.write.tag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.TagRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
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
