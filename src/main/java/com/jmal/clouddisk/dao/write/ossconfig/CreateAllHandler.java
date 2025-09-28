package com.jmal.clouddisk.dao.write.ossconfig;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.OssConfigRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("ossConfigCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<OssConfigOperation.CreateAll, Void> {

    private final OssConfigRepository repo;

    @Override
    public Void handle(OssConfigOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
