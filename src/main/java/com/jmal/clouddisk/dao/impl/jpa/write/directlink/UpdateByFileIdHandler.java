package com.jmal.clouddisk.dao.impl.jpa.write.directlink;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.DirectLinkRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.DirectLink;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("directUpdateByFileIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateByFileIdHandler implements IDataOperationHandler<DirectLinkOperation.UpdateByFileId, Void> {

    private final DirectLinkRepository repo;

    @Override
    public Void handle(DirectLinkOperation.UpdateByFileId op) {
        DirectLink directLink = repo.findByFileId(op.fileId());
        if (directLink == null) {
            directLink = new DirectLink();
        }
        directLink.setFileId(op.fileId());
        directLink.setMark(op.mark());
        directLink.setUserId(op.userId());
        directLink.setUpdateDate(op.now());
        repo.save(directLink);
        return null;
    }
}
