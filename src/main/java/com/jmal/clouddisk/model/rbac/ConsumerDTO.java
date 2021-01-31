package com.jmal.clouddisk.model.rbac;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 用户模型传输对象
 * @author jmal
 */
@Data
@ApiModel
@Valid
public class ConsumerDTO {
    @Id
    String id;
    @NotNull(message = "用户账号不能为空")
    @ApiModelProperty(name = "username", value = "用户账号", example = "admin", required = true)
    String username;
    @NotNull(message = "用户名不能为空")
    @ApiModelProperty(name = "showName", value = "用户名", example = "管理员1", required = true)
    String showName;
    @ApiModelProperty(name = "password", value = "密码", example = "123456")
    String password;
    String encryptPwd;
    @ApiModelProperty(name = "avatar", value = "头像")
    String avatar;
    @ApiModelProperty(name = "slogan", value = "标语")
    String slogan;
    @ApiModelProperty(name = "introduction", value = "简介")
    String introduction;
    @ApiModelProperty(name = "webpDisabled", value = "是否禁用webp")
    Boolean webpDisabled;
    @ApiModelProperty(name = "roles", value = "角色Id集合")
    List<String> roles;
    @ApiModelProperty(name = "roleList", value = "角色集合", hidden = true)
    List<RoleDTO> roleList;
    @ApiModelProperty(name = "quota", value = "默认配额, 10G", example = "10")
    Integer quota;
    @ApiModelProperty(name = "takeUpSpace", value = "已使用的空间")
    Long takeUpSpace;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(name = "createTime", value = "创建时间", hidden = true)
    LocalDateTime createTime;

}
