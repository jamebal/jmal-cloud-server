package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.List;

/**
 * @author jmal
 * @Description LdapDO
 * @date 2023/5/8 18:00
 */
@Data
@Schema
@Valid
public class LdapConfigDTO implements Reflective {

    @Schema(name = "enable", title = "是否启用", example = "true")
    Boolean enable;

    @NotNull(message = "ldap服务器不能为空")
    @Schema(name = "ldapServer", title = "ldap服务器", example = "localhost:8389")
    String ldapServer;

    @NotNull(message = "默认角色不能为空")
    @Schema(name = "defaultRoleList", title = "默认角色")
    List<String> defaultRoleList;

    @NotNull(message = "账号密码不能为空")
    @Schema(name = "password", title = "账号密码", example = "password")
    String password;

    @NotNull(message = "Base DN不能为空")
    @Schema(name = "baseDN", title = "Base DN", example = "dc=tes,dc=com")
    String baseDN;

    @NotNull(message = "User DN不能为空")
    @Schema(name = "userDN", title = "User DN", example = "cn=admin,dc=tes,dc=com")
    String userDN;

    @NotNull(message = "登录名不能为空")
    @Schema(name = "loginName", title = "登录名", example = "uid")
    String loginName;

    public LdapConfigDO toLdapConfigDO(String userId, TextEncryptor textEncryptor) {
        LdapConfigDO ldapConfigDO = new LdapConfigDO();
        ldapConfigDO.setId("6458f8c5bb943e3cf1db5f29");
        ldapConfigDO.setEnable(this.enable);
        ldapConfigDO.setLdapServer(this.ldapServer);
        ldapConfigDO.setDefaultRoleList(this.defaultRoleList);
        ldapConfigDO.setPassword(textEncryptor.encrypt(password));
        ldapConfigDO.setBaseDN(this.baseDN);
        ldapConfigDO.setUserDN(this.userDN);
        ldapConfigDO.setLoginName(this.loginName);
        ldapConfigDO.setUserId(userId);
        return ldapConfigDO;
    }

}
