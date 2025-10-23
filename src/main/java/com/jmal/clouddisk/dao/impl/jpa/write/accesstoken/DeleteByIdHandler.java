package com.jmal.clouddisk.dao.impl.jpa.write.accesstoken;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.AccessTokenRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("accessTokenDeleteByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteByIdHandler implements IDataOperationHandler<AccessTokenOperation.DeleteById, Void> {

    private final AccessTokenRepository repo;

    @Override
    public Void handle(AccessTokenOperation.DeleteById operation) {
        repo.deleteById(operation.id());
        return null;
    }
}
