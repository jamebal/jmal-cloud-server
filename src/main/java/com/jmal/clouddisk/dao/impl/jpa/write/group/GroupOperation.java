package com.jmal.clouddisk.dao.impl.jpa.write.group;

import com.jmal.clouddisk.model.rbac.GroupDO;

import java.util.List;

public final class GroupOperation {
    private GroupOperation() {}

    public record CreateAll(Iterable<GroupDO> entities) implements IGroupOperation<Void> {}
    public record Create(GroupDO entity) implements IGroupOperation<GroupDO> {}
    public record RemoveByIdIn(List<String> groupIds) implements IGroupOperation<Void> {}

}
