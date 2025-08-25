package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.List;

/**
 * @author jmal
 * @Description LdapDO
 * @date 2023/5/8 18:00
 */
@Data
@Document(collection = "ldapConfig")
public class LdapConfigDO implements Reflective {
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
     * 默认角色
     */
    List<String> defaultRoleList;
    /**
     * 管理账号密码
     */
    String password;
    /**
     * baseDN
     */
    String baseDN;
    /**
     * User DN
     */
    String userDN;
    /**
     * LDAP服务器中对应个人用户名的字段
     */
    String loginName;

    String userId;

    public LdapConfigDTO toLdapConfigDTO(TextEncryptor textEncryptor) {
        LdapConfigDTO ldapConfigDTO = new LdapConfigDTO();
        ldapConfigDTO.setEnable(this.enable);
        ldapConfigDTO.setLdapServer(this.ldapServer);
        ldapConfigDTO.setDefaultRoleList(this.defaultRoleList);
        ldapConfigDTO.setPassword(textEncryptor.decrypt(this.password));
        ldapConfigDTO.setBaseDN(this.baseDN);
        ldapConfigDTO.setUserDN(this.userDN);
        ldapConfigDTO.setLoginName(this.loginName);
        return ldapConfigDTO;
    }

    public LdapConfigDTO toLdapConfigDTO() {
        LdapConfigDTO ldapConfigDTO = new LdapConfigDTO();
        ldapConfigDTO.setEnable(this.enable);
        ldapConfigDTO.setLdapServer(this.ldapServer);
        ldapConfigDTO.setDefaultRoleList(this.defaultRoleList);
        ldapConfigDTO.setBaseDN(this.baseDN);
        ldapConfigDTO.setUserDN(this.userDN);
        ldapConfigDTO.setLoginName(this.loginName);
        return ldapConfigDTO;
    }
}
