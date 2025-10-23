package com.jmal.clouddisk.dao.impl.jpa.write.log;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface ILogDataOperation<R> extends IDataOperation<R>
        permits LogDataOperation.Create, LogDataOperation.CreateAll {

}
