package com.jmal.clouddisk.dao.impl.jpa.write.officeconfig;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.OfficeConfigRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("officeConfigCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<OfficeConfigOperation.CreateAll, Void> {

    private final OfficeConfigRepository repo;

    @Override
    public Void handle(OfficeConfigOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
