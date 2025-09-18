package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateLuceneIndexStatusByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateLuceneIndexStatusByIdHandler implements IDataOperationHandler<FileOperation.UpdateLuceneIndexStatusByIdIn, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.UpdateLuceneIndexStatusByIdIn op) {
        repo.updateLuceneIndexStatusByIdIn(op.fileIdList(), op.indexStatus());
        return null;
    }
}
