package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetNameByMountFileIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetNameByMountFileIdHandler implements IDataOperationHandler<FileOperation.SetNameByMountFileId, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.SetNameByMountFileId op) {
        repo.setNameByMountFileId(op.newFileName(), op.fileId());
        return null;
    }
}
