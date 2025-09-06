package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileCreateFileMetadataHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateFileMetadataHandler implements IDataOperationHandler<FileOperation.CreateFileMetadata, FileMetadataDO> {

    private final FileMetadataRepository repo;

    @Override
    public FileMetadataDO handle(FileOperation.CreateFileMetadata op) {
        return repo.save(op.entity());
    }
}
