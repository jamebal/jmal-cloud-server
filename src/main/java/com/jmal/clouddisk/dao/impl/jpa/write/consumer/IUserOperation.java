package com.jmal.clouddisk.dao.impl.jpa.write.consumer;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IUserOperation<R> extends IDataOperation<R>
        permits UserOperation.Create, UserOperation.CreateAll, UserOperation.DeleteAllById, UserOperation.ResetAdminPassword {

}
