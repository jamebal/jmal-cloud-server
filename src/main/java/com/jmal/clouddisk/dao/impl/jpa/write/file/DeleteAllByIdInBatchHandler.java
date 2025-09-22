package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileDeleteAllByIdInBatchHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteAllByIdInBatchHandler implements IDataOperationHandler<FileOperation.DeleteAllByIdInBatch, Void> {

    private final FileMetadataRepository repo;
    private final FilePropsRepository filePropsRepository;
    private final FilePersistenceService filePersistenceService;
    private final ArticleRepository articleRepository;

    @Override
    public Void handle(FileOperation.DeleteAllByIdInBatch op) {
        repo.deleteAllByPublicIdIn(op.fileIdList());
        filePropsRepository.deleteAllByPublicIdIn(op.fileIdList());
        articleRepository.deleteAllByPublicIdIn(op.fileIdList());
        filePersistenceService.deleteContents(op.fileIdList());
        return null;
    }
}
