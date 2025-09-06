package com.jmal.clouddisk.dao.impl.jpa.write.trash;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TrashRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("trashCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<TrashOperation.CreateAll, Void> {

    private final TrashRepository repo;

    @Override
    public Void handle(TrashOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
