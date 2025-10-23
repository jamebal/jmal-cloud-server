package com.jmal.clouddisk.dao.impl.jpa.write.transcodecnofig;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TranscodeConfigRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("transcodeConfigCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<TranscodeConfigOperation.CreateAll, Void> {

    private final TranscodeConfigRepository repo;

    @Override
    public Void handle(TranscodeConfigOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
