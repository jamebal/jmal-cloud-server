package com.jmal.clouddisk.dao.impl.jpa.write.access_token;

import com.jmal.clouddisk.model.UserAccessTokenDO;

public final class AccessTokenOperation {

    private AccessTokenOperation() {}

    public record CreateAll(Iterable<UserAccessTokenDO> entities) implements IAccessTokenOperation {}
}
