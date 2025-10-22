package com.jmal.clouddisk.dao.impl.jpa.write.consumer;

import com.jmal.clouddisk.model.rbac.ConsumerDO;

import java.util.List;

public final class UserOperation {
    private UserOperation() {}

    public record CreateAll(Iterable<ConsumerDO> entities) implements IUserOperation<Void> {}
    public record Create(ConsumerDO entity) implements IUserOperation<ConsumerDO> {}
    public record DeleteAllById(List<String> idList) implements IUserOperation<Void> {}

    public record ResetAdminPassword(String password) implements IUserOperation<Boolean> {}
}
