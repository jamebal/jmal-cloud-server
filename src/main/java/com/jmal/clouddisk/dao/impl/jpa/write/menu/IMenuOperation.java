package com.jmal.clouddisk.dao.impl.jpa.write.menu;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IMenuOperation extends IDataOperation
        permits MenuOperation.Create,
        MenuOperation.CreateAll,
        MenuOperation.Update,
        MenuOperation.Delete {
}
