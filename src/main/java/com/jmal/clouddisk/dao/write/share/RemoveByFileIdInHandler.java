package com.jmal.clouddisk.dao.write.share;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.ShareRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("shareRemoveByFileIdInHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByFileIdInHandler implements IDataOperationHandler<ShareOperation.RemoveByFileIdIn, Void> {

    private final ShareRepository repo;

    @Override
    public Void handle(ShareOperation.RemoveByFileIdIn op) {
        repo.removeByFileIdIn(op.fileIdList());
        return null;
    }
}
