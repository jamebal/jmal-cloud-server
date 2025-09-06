package com.jmal.clouddisk.dao.impl.jpa.write.role;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IRoleOperation<R> extends IDataOperation<R>
        permits RoleOperation.CreateAll {

}
