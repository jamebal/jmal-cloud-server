package com.jmal.clouddisk.dao.impl.jpa.write.burnnote;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.BurnNoteJpaRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.BurnNoteDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("burnNoteCreateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHandler implements IDataOperationHandler<BurnNoteOperation.Create, BurnNoteDO> {

    private final BurnNoteJpaRepository repo;

    @Override
    public BurnNoteDO handle(BurnNoteOperation.Create op) {
        return repo.save(op.entities());
    }
}
