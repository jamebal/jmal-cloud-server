package com.jmal.clouddisk.dao.impl.jpa.write.access_token;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IAccessTokenOperation extends IDataOperation
        permits AccessTokenOperation.Create, AccessTokenOperation.CreateAll, AccessTokenOperation.DeleteById, AccessTokenOperation.DeleteByUsernameIn, AccessTokenOperation.UpdateLastActiveTimeByUsernameAndToken {
}
