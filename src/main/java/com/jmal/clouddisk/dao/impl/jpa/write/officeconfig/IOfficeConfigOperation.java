package com.jmal.clouddisk.dao.impl.jpa.write.officeconfig;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IOfficeConfigOperation<R> extends IDataOperation<R>
        permits OfficeConfigOperation.CreateAll, OfficeConfigOperation.Upsert {

}
