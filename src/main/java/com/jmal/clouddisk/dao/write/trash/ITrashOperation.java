package com.jmal.clouddisk.dao.write.trash;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface ITrashOperation<R> extends IDataOperation<R>
        permits TrashOperation.CreateAll, TrashOperation.DeleteAll, TrashOperation.DeleteAllByIdInBatch, TrashOperation.DeleteById {

}
