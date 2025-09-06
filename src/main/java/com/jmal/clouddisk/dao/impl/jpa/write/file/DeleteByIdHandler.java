package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileDeleteByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteByIdHandler implements IDataOperationHandler<FileOperation.DeleteById, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.DeleteById op) {
        repo.deleteById(op.fileId());
        return null;
    }
}
