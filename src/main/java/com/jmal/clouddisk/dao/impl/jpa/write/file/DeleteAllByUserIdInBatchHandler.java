package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("fileDeleteAllByUserIdInBatchHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteAllByUserIdInBatchHandler implements IDataOperationHandler<FileOperation.DeleteAllByUserIdInBatch, Void> {

    private final FileMetadataRepository repo;
    private final FilePropsRepository filePropsRepository;
    private final FilePersistenceService filePersistenceService;

    @Override
    public Void handle(FileOperation.DeleteAllByUserIdInBatch op) {
        List<String> ids = repo.findAllIdsByUserIdIn(op.userIdList());
        repo.deleteAllByPublicIdIn(ids);
        filePropsRepository.deleteAllByPublicIdIn(ids);
        filePersistenceService.deleteContents(ids);
        return null;
    }
}
