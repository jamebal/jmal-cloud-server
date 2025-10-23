package com.jmal.clouddisk.dao.impl.jpa.write.trash;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.TrashRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("trashDeleteAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteAllHandler implements IDataOperationHandler<TrashOperation.DeleteAll, Void> {

    private final TrashRepository repo;

    private final FilePersistenceService filePersistenceService;

    @Override
    public Void handle(TrashOperation.DeleteAll op) {
        repo.deleteAllByPublicIdIn(op.ids());
        filePersistenceService.deleteContents(op.ids());
        return null;
    }
}
