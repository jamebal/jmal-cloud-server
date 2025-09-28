package com.jmal.clouddisk.dao.write.etag;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FileEtagRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("etagClearMarkUpdateByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ClearMarkUpdateByIdHandler implements IDataOperationHandler<EtagOperation.ClearMarkUpdateById, Void> {

    private final FileEtagRepository repo;

    @Override
    public Void handle(EtagOperation.ClearMarkUpdateById op) {
        repo.clearMarkUpdateById(op.fileId());
        return null;
    }
}
