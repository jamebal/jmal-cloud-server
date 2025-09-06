package com.jmal.clouddisk.dao.impl.jpa.write.tag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TagRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("tagCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<TagOperation.CreateAll, Void> {

    private final TagRepository repo;

    @Override
    public Void handle(TagOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
