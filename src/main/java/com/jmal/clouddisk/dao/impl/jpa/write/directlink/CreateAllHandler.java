package com.jmal.clouddisk.dao.impl.jpa.write.directlink;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.DirectLinkRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("directLinkCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<DirectLinkOperation.CreateAll, Void> {

    private final DirectLinkRepository repo;

    @Override
    public Void handle(DirectLinkOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
