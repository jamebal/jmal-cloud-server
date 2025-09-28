package com.jmal.clouddisk.dao.write.accesstoken;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IAccessTokenOperation<R> extends IDataOperation<R>
        permits AccessTokenOperation.Create, AccessTokenOperation.CreateAll, AccessTokenOperation.DeleteById, AccessTokenOperation.DeleteByUsernameIn, AccessTokenOperation.UpdateLastActiveTimeByUsernameAndToken {
}
