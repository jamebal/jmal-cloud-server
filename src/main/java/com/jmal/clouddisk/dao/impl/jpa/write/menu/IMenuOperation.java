package com.jmal.clouddisk.dao.impl.jpa.write.menu;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IMenuOperation<R> extends IDataOperation<R>
        permits MenuOperation.Create, MenuOperation.CreateAll, MenuOperation.Delete, MenuOperation.RemoveByIdIn, MenuOperation.Update {
}
