package com.jmal.clouddisk.dao.impl.jpa.write.ossconfig;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.OssConfigRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("ossDeleteByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteByIdHandler implements IDataOperationHandler<OssConfigOperation.DeleteById, Void> {

    private final OssConfigRepository repo;

    @Override
    public Void handle(OssConfigOperation.DeleteById op) {
        repo.deleteById(op.id());
        return null;
    }
}
