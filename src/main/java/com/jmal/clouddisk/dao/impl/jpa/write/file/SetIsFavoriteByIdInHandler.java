package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetIsFavoriteByIdInHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetIsFavoriteByIdInHandler implements IDataOperationHandler<FileOperation.SetIsFavoriteByIdIn, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.SetIsFavoriteByIdIn op) {
        repo.setIsFavoriteByIdIn(op.fileIds(), op.isFavorite());
        return null;
    }
}
