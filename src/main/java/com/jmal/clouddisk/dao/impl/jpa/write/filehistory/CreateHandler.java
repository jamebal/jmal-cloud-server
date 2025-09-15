package com.jmal.clouddisk.dao.impl.jpa.write.filehistory;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileHistoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.file.FileHistoryDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component("fileHistoryCreateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHandler implements IDataOperationHandler<FileHistoryOperation.Create, Void> {

    private final FileHistoryRepository repo;

    private final FilePersistenceService persistenceService;

    @Override
    public Void handle(FileHistoryOperation.Create op) {
        FileHistoryDO fileHistoryDO = op.entity();
        FileHistoryDO saved = repo.save(fileHistoryDO);
        try (InputStream inputStream = op.inputStream()) {
            persistenceService.persistFileHistory(fileHistoryDO.getFileId(), inputStream, saved.getId());
        } catch (IOException e) {
            throw new CommonException(e.getMessage());
        }
        return null;
    }
}
