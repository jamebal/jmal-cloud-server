package com.jmal.clouddisk.dao.impl.jpa.write.role;

import com.jmal.clouddisk.model.rbac.RoleDO;

public final class RoleOperation {
    private RoleOperation() {}

    public record CreateAll(Iterable<RoleDO> entities) implements IRoleOperation {}
}
