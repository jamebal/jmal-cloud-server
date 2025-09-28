package com.jmal.clouddisk.dao.write.officeconfig;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IOfficeConfigOperation<R> extends IDataOperation<R>
        permits OfficeConfigOperation.CreateAll, OfficeConfigOperation.Upsert {

}
