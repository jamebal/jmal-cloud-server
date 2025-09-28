package com.jmal.clouddisk.dao.write.consumer;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IUserOperation<R> extends IDataOperation<R>
        permits UserOperation.Create, UserOperation.CreateAll, UserOperation.DeleteAllById {

}
