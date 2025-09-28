package com.jmal.clouddisk.dao.write.role;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IRoleOperation<R> extends IDataOperation<R>
        permits RoleOperation.Create, RoleOperation.CreateAll, RoleOperation.removeByIdIn {

}
