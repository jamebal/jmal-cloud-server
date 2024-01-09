package com.jmal.clouddisk.model;

import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * @author jmal
 * @Description LdapDO
 * @date 2023/5/8 18:00
 */
@Data
@Schema
@Valid
public class LdapConfigDTO {

    @Schema(name = "enable", title = "是否启用", example = "true")
    Boolean enable;

    @NotNull(message = "ldap服务器不能为空")
    @Schema(name = "ldapServer", title = "ldap服务器", example = "localhost:8389")
    String ldapServer;

    @NotNull(message = "默认角色不能为空")
    @Schema(name = "defaultRoleList", title = "默认角色", example = "")
    List<String> defaultRoleList;

    @NotNull(message = "账号密码不能为空")
    @Schema(name = "password", title = "账号密码", example = "password")
    String password;

    @NotNull(message = "Base DN不能为空")
    @Schema(name = "baseDN", title = "Base DN", example = "dc=tes,dc=com")
    String baseDN;

    @NotNull(message = "登录名不能为空")
    @Schema(name = "loginName", title = "登录名", example = "uid")
    String loginName;

    public LdapConfigDO toLdapConfigDO(ConsumerDO consumerDO) {
        LdapConfigDO ldapConfigDO = new LdapConfigDO();
        ldapConfigDO.setId("6458f8c5bb943e3cf1db5f29");
        ldapConfigDO.setEnable(this.enable);
        ldapConfigDO.setLdapServer(this.ldapServer);
        ldapConfigDO.setDefaultRoleList(this.defaultRoleList);
        ldapConfigDO.setPassword(UserServiceImpl.getEncryptPwd(this.password, consumerDO.getPassword()));
        ldapConfigDO.setBaseDN(this.baseDN);
        ldapConfigDO.setLoginName(this.loginName);
        ldapConfigDO.setUserId(consumerDO.getId());
        return ldapConfigDO;
    }

}
