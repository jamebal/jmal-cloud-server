package com.jmal.clouddisk.dao.impl.jpa.write.share;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ShareRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("shareSetFileNameByFileIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetFileNameByFileIdHandler implements IDataOperationHandler<ShareOperation.SetFileNameByFileId, Void> {

    private final ShareRepository repo;

    @Override
    public Void handle(ShareOperation.SetFileNameByFileId op) {
        repo.SetFileNameByFileId(op.fileId(), op.newFileName());
        return null;
    }
}
