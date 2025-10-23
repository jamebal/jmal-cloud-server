package com.jmal.clouddisk.dao.impl.jpa.write.directlink;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.DirectLinkRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("directDeleteByUserIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteByUserIdHandler implements IDataOperationHandler<DirectLinkOperation.DeleteByUserId, Void> {

    private final DirectLinkRepository repo;

    @Override
    public Void handle(DirectLinkOperation.DeleteByUserId op) {
        repo.deleteByUserId(op.userId());
        return null;
    }
}
