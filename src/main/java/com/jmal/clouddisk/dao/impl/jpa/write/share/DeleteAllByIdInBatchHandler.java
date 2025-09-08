package com.jmal.clouddisk.dao.impl.jpa.write.share;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ShareRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("shareDeleteAllByIdInBatchHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteAllByIdInBatchHandler implements IDataOperationHandler<ShareOperation.DeleteAllByIdInBatch, Void> {

    private final ShareRepository repo;

    @Override
    public Void handle(ShareOperation.DeleteAllByIdInBatch op) {
        repo.deleteAllByIdInBatch(op.roleIdList());
        return null;
    }
}
