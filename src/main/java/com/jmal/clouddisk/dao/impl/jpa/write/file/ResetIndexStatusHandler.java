package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileResetIndexStatusHandler")
@RequiredArgsConstructor
@Slf4j
@Conditional(RelationalDataSourceCondition.class)
public class ResetIndexStatusHandler implements IDataOperationHandler<FileOperation.ResetIndexStatus, Void> {

    private final FileMetadataRepository repo;

    @Override
    public Void handle(FileOperation.ResetIndexStatus op) {
        try {
            repo.resetIndexStatus();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
