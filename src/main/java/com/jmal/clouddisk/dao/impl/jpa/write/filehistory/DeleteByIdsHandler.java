package com.jmal.clouddisk.dao.impl.jpa.write.filehistory;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileHistoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileDeleteByIdsHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteByIdsHandler implements IDataOperationHandler<FileHistoryOperation.DeleteByIds, Void> {

    private final FileHistoryRepository repo;

    private final FilePersistenceService persistenceService;

    @Override
    public Void handle(FileHistoryOperation.DeleteByIds op) {
        op.fileHistoryIds().forEach(id -> {
            String fileId = repo.findFileIdById(id);
            if (fileId == null) {
                return;
            }
            repo.deleteById(id);
            persistenceService.deleteFileHistory(fileId, id);
        });
        return null;
    }
}
