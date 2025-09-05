package com.jmal.clouddisk.dao.impl.jpa.write.consumer;

import com.jmal.clouddisk.model.rbac.ConsumerDO;

public final class UserOperation {
    private UserOperation() {}

    public record CreateAll(Iterable<ConsumerDO> entities) implements IUserOperation {}
}
