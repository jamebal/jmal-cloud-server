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

@Component("fileRemoveByUserIdAndPathAndNameHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByUserIdAndPathAndNameHandler implements IDataOperationHandler<FileOperation.RemoveByUserIdAndPathAndName, Void> {

    private final FileMetadataRepository repo;
    private final FilePersistenceService filePersistenceService;
    private final FilePropsRepository filePropsRepository;
    private final ArticleRepository articleRepository;

    @Override
    public Void handle(FileOperation.RemoveByUserIdAndPathAndName op) {
        String fileId = repo.findIdByUserIdAndPathAndName(op.userId() , op.path(), op.name()).orElse(null);
        if (fileId == null) {
            return null;
        }
        filePersistenceService.deleteContents(fileId);
        repo.removeByUserIdAndPathAndName(op.userId() , op.path(), op.name());
        filePropsRepository.deleteByPublicId(fileId);
        articleRepository.deleteByPublicId(fileId);
        return null;
    }
}
