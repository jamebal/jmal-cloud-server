package com.jmal.clouddisk.dao.write.filehistory;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileHistoryRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileHistoryCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<FileHistoryOperation.CreateAll, Void> {

    private final FileHistoryRepository repo;

    @Override
    public Void handle(FileHistoryOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
