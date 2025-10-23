package com.jmal.clouddisk.dao.impl.jpa.write.share;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ShareRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("shareRemoveByUserIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByUserIdHandler implements IDataOperationHandler<ShareOperation.removeByUserId, Void> {

    private final ShareRepository repo;

    @Override
    public Void handle(ShareOperation.removeByUserId op) {
        repo.removeByUserId(op.userId());
        return null;
    }
}
