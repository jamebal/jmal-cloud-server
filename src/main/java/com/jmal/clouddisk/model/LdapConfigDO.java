package com.jmal.clouddisk.model;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @author jmal
 * @Description LdapDO
 * @date 2023/5/8 18:00
 */
@Data
@Document(collection = "ldapConfig")
public class LdapConfigDO {
    String id;
    /**
     * 是否启用
     */
    Boolean enable;
    /**
     * ldap服务器
     */
    String ldapServer;
    /**
     * 端口号
     */
    String port;
    /**
     * 管理账号
     */
    String account;
    /**
     * 管理账号密码
     */
    String password;
    /**
     * baseDN
     */
    String baseDN;
    /**
     * LDAP服务器中对应个人用户名的字段
     */
    String loginName;

    String userId;
}
