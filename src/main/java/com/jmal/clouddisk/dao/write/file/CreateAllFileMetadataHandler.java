package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileMetadataRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileCreateAllFileMetadataHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllFileMetadataHandler implements IDataOperationHandler<FileOperation.CreateAllFileMetadata, Integer> {

    private final FileMetadataRepository repo;

    @Override
    public Integer handle(FileOperation.CreateAllFileMetadata op) {
        // 修改publicId 为 objectId
        return repo.saveAll(op.entities()).size();
    }
}
