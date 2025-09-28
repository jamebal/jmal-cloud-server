package com.jmal.clouddisk.dao.write.share;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.ShareRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import com.jmal.clouddisk.model.ShareDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("shareCreateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHandler implements IDataOperationHandler<ShareOperation.Create, ShareDO> {

    private final ShareRepository repo;

    @Override
    public ShareDO handle(ShareOperation.Create op) {
        return repo.save(op.entity());
    }
}
