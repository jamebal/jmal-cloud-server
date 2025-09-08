package com.jmal.clouddisk.dao.impl.jpa.write.tag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TagRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("tagCreateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHandler implements IDataOperationHandler<TagOperation.Create, Void> {

    private final TagRepository repo;

    @Override
    public Void handle(TagOperation.Create op) {
        repo.save(op.entity());
        return null;
    }
}
