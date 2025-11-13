package com.jmal.clouddisk.dao.impl.jpa.write.burnnote;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.BurnNoteJpaRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("burnNoteDeleteHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteHandler implements IDataOperationHandler<BurnNoteOperation.Delete, Void> {

    private final BurnNoteJpaRepository repo;

    @Override
    public Void handle(BurnNoteOperation.Delete op) {
        repo.deleteById(op.id());
        return null;
    }
}
