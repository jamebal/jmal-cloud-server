package com.jmal.clouddisk.dao.impl.jpa.write.log;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.LogRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.LogOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("logCreateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHandler implements IDataOperationHandler<LogDataOperation.Create, LogOperation> {

    private final LogRepository repo;

    @Override
    public LogOperation handle(LogDataOperation.Create op) {
        return repo.save(op.entity());
    }
}
