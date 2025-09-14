package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetPathByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetPathByIdHandler implements IDataOperationHandler<FileOperation.SetPathById, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.SetPathById op) {
        repo.setPathById(op.id(), op.newFilePath());
        return null;
    }
}
