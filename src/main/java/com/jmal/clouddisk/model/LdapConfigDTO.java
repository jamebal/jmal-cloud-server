package com.jmal.clouddisk.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * @author jmal
 * @Description LdapDO
 * @date 2023/5/8 18:00
 */
@Data
@Schema
@Valid
public class LdapConfigDTO {

    @Schema(hidden = true)
    String id;

    @Schema(name = "enable", title = "是否启用", example = "true")
    Boolean enable;

    @NotNull(message = "ldap服务器不能为空")
    @Schema(name = "ldapServer", title = "ldap服务器", example = "ldap.test.com:389")
    String ldapServer;

    @NotNull(message = "端口号不能为空")
    @Schema(name = "port", title = "端口号", example = "389")
    Integer port;

    @NotNull(message = "管理账号不能为空")
    @Schema(name = "account", title = "管理账号", example = "admin")
    String account;

    @NotNull(message = "账号密码不能为空")
    @Schema(name = "password", title = "账号密码", example = "password")
    String password;

    @NotNull(message = "Base DN不能为空")
    @Schema(name = "baseDN", title = "Base DN", example = "dc=tes,dc=com")
    String baseDN;

    @NotNull(message = "登录名不能为空")
    @Schema(name = "loginName", title = "登录名", example = "uid")
    String loginName;

}
