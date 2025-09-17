package com.jmal.clouddisk.dao.impl.jpa.write.officeconfig;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.OfficeConfigRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.office.model.OfficeConfigDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("officeUpsertHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpsertHandler implements IDataOperationHandler<OfficeConfigOperation.Upsert, Void> {

    private final OfficeConfigRepository repo;

    @Override
    public Void handle(OfficeConfigOperation.Upsert op) {
        OfficeConfigDO update = op.officeConfigDO();
        OfficeConfigDO officeConfigDO = repo.findAll().getFirst();
        if (officeConfigDO != null) {
            update.setId(officeConfigDO.getId());
        }
        repo.save(update);
        return null;
    }
}
