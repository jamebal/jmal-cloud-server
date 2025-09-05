package com.jmal.clouddisk.dao.impl.jpa.write.trash;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface ITrashOperation extends IDataOperation
        permits TrashOperation.CreateAll {

}
