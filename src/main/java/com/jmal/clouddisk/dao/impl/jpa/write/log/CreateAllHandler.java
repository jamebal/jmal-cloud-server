package com.jmal.clouddisk.dao.impl.jpa.write.log;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.LogRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("logCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<LogDataOperation.CreateAll, Void> {

    private final LogRepository repo;

    @Override
    public Void handle(LogDataOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
