package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateModifyFileHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateModifyFileHandler implements IDataOperationHandler<FileOperation.UpdateModifyFile, Integer> {

    private final FileMetadataRepository repo;

    @Override
    public Integer handle(FileOperation.UpdateModifyFile op) {
        return repo.updateModifyFile(op.id(), op.length(), op.md5(), op.suffix(), op.fileContentType(), op.updateTime());
    }
}
