package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileMetadataRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileResetIndexStatusHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ResetIndexStatusHandler implements IDataOperationHandler<FileOperation.ResetIndexStatus, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.ResetIndexStatus op) {
        repo.resetIndexStatus();
        return null;
    }
}
