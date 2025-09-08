package com.jmal.clouddisk.dao.impl.jpa.write.menu;

import com.jmal.clouddisk.model.rbac.MenuDO;

import java.util.Collection;

public final class MenuOperation {
    private MenuOperation() {}

    public record Create(MenuDO entity) implements IMenuOperation<Void> {}
    public record CreateAll(Iterable<MenuDO> entities) implements IMenuOperation<Void> {}
    public record Update(MenuDO entity) implements IMenuOperation<Void> {}
    public record Delete(MenuDO entity) implements IMenuOperation<Void> {}
    public record RemoveByIdIn(Collection<String> idList) implements IMenuOperation<Void> {}


}
