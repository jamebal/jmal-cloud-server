package com.jmal.clouddisk.dao.write.filehistory;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileHistoryRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateFileIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateFileIdHandler implements IDataOperationHandler<FileHistoryOperation.UpdateFileId, Void> {

    private final FileHistoryRepository repo;

    @Override
    public Void handle(FileHistoryOperation.UpdateFileId op) {
        repo.updateFileId(op.sourceFileId(), op.destinationFileId());
        return null;
    }
}
