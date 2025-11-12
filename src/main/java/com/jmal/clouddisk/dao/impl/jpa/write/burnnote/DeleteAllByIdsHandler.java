package com.jmal.clouddisk.dao.impl.jpa.write.burnnote;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.BurnNoteJpaRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("burnNoteDeleteAllByIdsHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteAllByIdsHandler implements IDataOperationHandler<BurnNoteOperation.DeleteAllByIds, Integer> {

    private final BurnNoteJpaRepository repo;

    @Override
    public Integer handle(BurnNoteOperation.DeleteAllByIds op) {
        return repo.deleteExpiredNotes(op.ids());
    }
}
