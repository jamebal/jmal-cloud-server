package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateSummaryHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateSummaryHandler implements IDataOperationHandler<FileOperation.UpdateSummary, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.UpdateSummary op) {
        repo.updateSummaryById(op.fileId(), op.summary());
        return null;
    }
}
