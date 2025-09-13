package com.jmal.clouddisk.dao.impl.jpa.write.trash;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface ITrashOperation<R> extends IDataOperation<R>
        permits TrashOperation.CreateAll, TrashOperation.DeleteAll, TrashOperation.DeleteById {

}
