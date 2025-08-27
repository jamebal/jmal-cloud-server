package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.dao.IAccessTokenDAO;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * @Description 用户授权码
 * @Author jmal
 * @Date 2020/9/30 10:34 上午
 */
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Document(collection = IAccessTokenDAO.ACCESS_TOKEN_COLLECTION_NAME)
@Table(name = IAccessTokenDAO.ACCESS_TOKEN_COLLECTION_NAME)
public class UserAccessTokenDO extends AuditableEntity implements Reflective {
    /***
     * 授权码名称
     */
    private String name;
    /***
     * 用户账号
     */
    private String username;
    /***
     * 用户授权码
     */
    private String accessToken;
    /***
     * 创建时间
     */
    LocalDateTime createTime;
    /***
     * 最近活动时间
     */
    LocalDateTime lastActiveTime;
}
