package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileMetadataRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUnsetDelTagHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UnsetDelTagHandler implements IDataOperationHandler<FileOperation.UnsetDelTag, Integer> {

    private final FileMetadataRepository repo;

    @Override
    public Integer handle(FileOperation.UnsetDelTag op) {
        return repo.unsetDelTag(op.fileId());
    }
}
