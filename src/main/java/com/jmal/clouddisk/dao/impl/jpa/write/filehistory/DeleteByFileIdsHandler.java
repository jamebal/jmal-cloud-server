package com.jmal.clouddisk.dao.impl.jpa.write.filehistory;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileHistoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileDeleteByFileIdsHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteByFileIdsHandler implements IDataOperationHandler<FileHistoryOperation.DeleteByFileIds, Void> {

    private final FileHistoryRepository repo;

    private final FilePersistenceService persistenceService;

    @Override
    public Void handle(FileHistoryOperation.DeleteByFileIds op) {
        repo.deleteAllByFileIdIn(op.fileIds());
        persistenceService.deleteContents(op.fileIds());
        return null;
    }
}
