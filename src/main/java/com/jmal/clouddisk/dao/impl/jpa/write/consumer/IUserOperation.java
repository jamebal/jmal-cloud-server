package com.jmal.clouddisk.dao.impl.jpa.write.consumer;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IUserOperation extends IDataOperation
        permits UserOperation.CreateAll {

}
