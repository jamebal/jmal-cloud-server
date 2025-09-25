package com.jmal.clouddisk.dao.impl.jpa.write.share;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ShareRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("shareUpdateSubShareHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateSubShareHandler implements IDataOperationHandler<ShareOperation.UpdateSubShare, Void> {

    private final ShareRepository repo;

    @Override
    public Void handle(ShareOperation.UpdateSubShare op) {
        repo.updateSubShare(op.subShareFileIdList(), op.isPrivacy(), op.id(), op.extractionCode());
        return null;
    }
}
