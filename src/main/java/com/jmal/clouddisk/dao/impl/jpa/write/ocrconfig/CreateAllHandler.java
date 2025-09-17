package com.jmal.clouddisk.dao.impl.jpa.write.ocrconfig;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.OcrConfigRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("ocrConfigCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<OcrConfigOperation.CreateAll, Void> {

    private final OcrConfigRepository repo;

    @Override
    public Void handle(OcrConfigOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
