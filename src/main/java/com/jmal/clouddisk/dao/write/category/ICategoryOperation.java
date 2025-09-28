package com.jmal.clouddisk.dao.write.category;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface ICategoryOperation<R> extends IDataOperation<R>
        permits CategoryOperation.CreateAll, CategoryOperation.DeleteAllByIdIn, CategoryOperation.UpdateSetDefaultFalseByDefaultIsTrue, CategoryOperation.UpdateSetDefaultTrueById, CategoryOperation.Upsert {

}
