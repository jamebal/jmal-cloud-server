package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.repository.jpa.ArticleRepository;
import com.jmal.clouddisk.dao.repository.jpa.FileMetadataRepository;
import com.jmal.clouddisk.dao.repository.jpa.FilePropsRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileDeleteByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteByIdHandler implements IDataOperationHandler<FileOperation.DeleteById, Void> {

    private final FileMetadataRepository repo;
    private final FilePropsRepository filePropsRepository;
    private final ArticleRepository articleRepository;
    private final FilePersistenceService filePersistenceService;

    @Override
    public Void handle(FileOperation.DeleteById op) {
        repo.deleteByPublicId(op.fileId());
        filePropsRepository.deleteByPublicId(op.fileId());
        articleRepository.deleteByPublicId(op.fileId());
        filePersistenceService.deleteContents(op.fileId());
        return null;
    }
}
