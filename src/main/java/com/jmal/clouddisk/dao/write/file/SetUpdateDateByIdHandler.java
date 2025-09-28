package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileMetadataRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetUpdateDateByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetUpdateDateByIdHandler implements IDataOperationHandler<FileOperation.SetUpdateDateById, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.SetUpdateDateById op) {
        repo.setUpdateDateById(op.fileId(), op.time());
        return null;
    }
}
