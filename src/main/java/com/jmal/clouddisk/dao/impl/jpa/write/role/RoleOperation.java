package com.jmal.clouddisk.dao.impl.jpa.write.role;

import com.jmal.clouddisk.model.rbac.RoleDO;

import java.util.List;

public final class RoleOperation {
    private RoleOperation() {}

    public record CreateAll(Iterable<RoleDO> entities) implements IRoleOperation<Void> {}
    public record Create(RoleDO entity) implements IRoleOperation<Void> {}
    public record removeByIdIn(List<String> roleIdList) implements IRoleOperation<Void> {}
}
