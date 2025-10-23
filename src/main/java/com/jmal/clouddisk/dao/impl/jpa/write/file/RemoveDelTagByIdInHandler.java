package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileRemoveDelTagByIdInHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveDelTagByIdInHandler implements IDataOperationHandler<FileOperation.UnsetDelTagByIdIn, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.UnsetDelTagByIdIn op) {
        if (op.fileIdList() == null || op.fileIdList().isEmpty()) {
            repo.unsetDelTag();
        } else {
            repo.unsetDelTagByIdIn(op.fileIdList());
        }
        return null;
    }
}
