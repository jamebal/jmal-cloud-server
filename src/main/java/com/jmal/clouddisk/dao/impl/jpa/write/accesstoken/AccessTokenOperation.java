package com.jmal.clouddisk.dao.impl.jpa.write.accesstoken;

import com.jmal.clouddisk.model.UserAccessTokenDO;

import java.time.LocalDateTime;
import java.util.List;

public final class AccessTokenOperation {

    private AccessTokenOperation() {}

    public record CreateAll(Iterable<UserAccessTokenDO> entities) implements IAccessTokenOperation<Void> {}
    public record Create(UserAccessTokenDO entity) implements IAccessTokenOperation<Void> {}
    public record DeleteByUsernameIn(List<String> usernames) implements IAccessTokenOperation<Void> {}
    public record UpdateLastActiveTimeByUsernameAndToken(String username, String token, LocalDateTime lastActiveTime) implements IAccessTokenOperation<Void> {}
    public record DeleteById(String id) implements IAccessTokenOperation<Void> {}

}
