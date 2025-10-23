package com.jmal.clouddisk.dao.impl.jpa.write.etag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileEtagRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("setRetryAtByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class setRetryAtByIdHandler implements IDataOperationHandler<EtagOperation.setRetryAtById, Void> {

    private final FileEtagRepository repo;

    @Override
    public Void handle(EtagOperation.setRetryAtById op) {
        repo.setRetryAtById(op.fileId(), op.nextRetryTime(), op.attempts());
        return null;
    }
}
