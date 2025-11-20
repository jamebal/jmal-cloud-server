package com.jmal.clouddisk.dao.impl.jpa.write.group;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IGroupOperation<R> extends IDataOperation<R>
        permits GroupOperation.Create, GroupOperation.CreateAll, GroupOperation.RemoveByIdIn {

}
