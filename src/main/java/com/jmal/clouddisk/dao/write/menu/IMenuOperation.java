package com.jmal.clouddisk.dao.write.menu;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IMenuOperation<R> extends IDataOperation<R>
        permits MenuOperation.Create, MenuOperation.CreateAll, MenuOperation.Delete, MenuOperation.RemoveByIdIn, MenuOperation.Update {
}
