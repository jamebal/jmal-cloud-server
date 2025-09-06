package com.jmal.clouddisk.dao.impl.jpa.write.menu;

import com.jmal.clouddisk.model.rbac.MenuDO;

public final class MenuOperation {
    private MenuOperation() {}

    public record Create(MenuDO entity) implements IMenuOperation<Void> {}
    public record CreateAll(Iterable<MenuDO> entities) implements IMenuOperation<Void> {}
    public record Update(MenuDO entity) implements IMenuOperation<Void> {}
    public record Delete(MenuDO entity) implements IMenuOperation<Void> {}
}
