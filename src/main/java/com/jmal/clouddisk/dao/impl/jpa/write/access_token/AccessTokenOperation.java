package com.jmal.clouddisk.dao.impl.jpa.write.access_token;

import com.jmal.clouddisk.model.UserAccessTokenDO;

import java.time.LocalDateTime;
import java.util.List;

public final class AccessTokenOperation {

    private AccessTokenOperation() {}

    public record CreateAll(Iterable<UserAccessTokenDO> entities) implements IAccessTokenOperation {}
    public record Create(UserAccessTokenDO entity) implements IAccessTokenOperation {}
    public record DeleteByUsernameIn(List<String> usernames) implements IAccessTokenOperation {}
    public record UpdateLastActiveTimeByUsernameAndToken(String username, String token, LocalDateTime lastActiveTime) implements IAccessTokenOperation {}
    public record DeleteById(String id) implements IAccessTokenOperation {}

}
