package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.UserAccessTokenDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UserAccessToken JPA Repository
 * @author jamebal
 */
@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface UserAccessTokenRepository extends JpaRepository<UserAccessTokenDO, String> {

    /**
     * 根据访问令牌查找
     */
    Optional<UserAccessTokenDO> findByAccessToken(String accessToken);

    /**
     * 根据用户名查找所有令牌
     */
    List<UserAccessTokenDO> findByUsername(String username);

    /**
     * 检查名称是否存在
     */
    boolean existsByName(String name);

    /**
     * 根据用户名列表删除令牌
     */
    @Modifying
    @Query("DELETE FROM UserAccessTokenDO u WHERE u.username IN :usernames")
    void deleteByUsernameIn(@Param("usernames") List<String> usernames);

    /**
     * 更新最后活跃时间
     */
    @Modifying
    @Query("UPDATE UserAccessTokenDO u SET u.lastActiveTime = :lastActiveTime WHERE u.username = :username AND u.accessToken = :token")
    int updateLastActiveTimeByUsernameAndToken(@Param("username") String username,
                                              @Param("token") String token,
                                              @Param("lastActiveTime") LocalDateTime lastActiveTime);
}
