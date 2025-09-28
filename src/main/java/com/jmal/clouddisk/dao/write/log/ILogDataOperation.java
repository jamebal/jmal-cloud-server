package com.jmal.clouddisk.dao.write.log;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface ILogDataOperation<R> extends IDataOperation<R>
        permits LogDataOperation.Create, LogDataOperation.CreateAll {

}
