package com.jmal.clouddisk.dao.impl.jpa.write.role;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IRoleOperation extends IDataOperation
        permits RoleOperation.CreateAll {

}
