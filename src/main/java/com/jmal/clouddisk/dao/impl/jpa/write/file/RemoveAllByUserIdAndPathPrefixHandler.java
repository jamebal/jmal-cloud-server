package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("fileRemoveAllByUserIdAndPathPrefixHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveAllByUserIdAndPathPrefixHandler implements IDataOperationHandler<FileOperation.RemoveAllByUserIdAndPathPrefix, Integer> {

    private final FileMetadataRepository repo;
    private final FilePersistenceService filePersistenceService;

    @Override
    public Integer handle(FileOperation.RemoveAllByUserIdAndPathPrefix op) {
        List<String> fileIdsToDelete = repo.findAllIdsByUserIdAndPathPrefix(op.userId(), op.pathPrefix());
        filePersistenceService.deleteContents(fileIdsToDelete);
        return repo.removeAllByUserIdAndPathPrefix(op.userId(), op.pathPrefix());
    }
}
