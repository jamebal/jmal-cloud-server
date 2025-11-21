package com.jmal.clouddisk.dao.impl.jpa.write.consumer;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.UserRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.model.rbac.Personalization;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("consumerCreateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHandler implements IDataOperationHandler<UserOperation.Create, ConsumerDO> {

    private final UserRepository repo;

    @Override
    public ConsumerDO handle(UserOperation.Create op) {
        ConsumerDO consumerDO = op.entity();
        if (consumerDO.getPersonalization() == null) {
            consumerDO.setPersonalization(new Personalization());
        }
        if (consumerDO.getGroups() == null) {
            consumerDO.setGroups(List.of(""));
        }
        if (consumerDO.getRoles() == null) {
            consumerDO.setRoles(List.of(""));
        }
        return repo.save(consumerDO);
    }
}
