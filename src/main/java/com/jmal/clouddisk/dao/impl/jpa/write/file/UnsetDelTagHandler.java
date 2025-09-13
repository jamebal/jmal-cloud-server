package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUnsetDelTagHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UnsetDelTagHandler implements IDataOperationHandler<FileOperation.UnsetDelTag, Long> {

    private final FileMetadataRepository repo;

    @Override
    public Long handle(FileOperation.UnsetDelTag op) {
        return repo.unsetDelTag(op.fileId());
    }
}
