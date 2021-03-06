package com.jmal.clouddisk.model.rbac;

import com.jmal.clouddisk.service.impl.UserServiceImpl;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 用户模型
 * @author jmal
 */
@Data
@ApiModel
@Document(collection = UserServiceImpl.COLLECTION_NAME)
public class ConsumerDO {
    String id;
    @ApiModelProperty(name = "username", value = "用户名", example = "admin")
    @Indexed
    String username;
    @Indexed
    @ApiModelProperty(name = "showName", value = "显示用户名", example = "管理员1")
    String showName;
    @ApiModelProperty(name = "password", value = "密码", example = "123456")
    String password;
    @ApiModelProperty(hidden = true, value = "密码加密后的字符串")
    String encryptPwd;
    @ApiModelProperty(value = "头像", example = "https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif")
    String avatar;
    @ApiModelProperty(name = "slogan", value = "标语")
    String slogan;
    @ApiModelProperty(name = "introduction", value = "简介")
    String introduction;
    @ApiModelProperty(name = "webpDisabled", value = "是否禁用webp")
    Boolean webpDisabled;
    @ApiModelProperty(name = "roles", value = "角色Id集合")
    List<String> roles;
    @ApiModelProperty(name = "quota", value = "默认配额, 10G", example = "10")
    Integer quota;
    @ApiModelProperty(name = "takeUpSpace", value = "已使用的空间")
    Long takeUpSpace;
    @ApiModelProperty(name = "createTime", value = "创建时间")
    LocalDateTime createTime;
    @ApiModelProperty(name = "updateTime", value = "修改时间", hidden = true)
    LocalDateTime updateTime;
    @ApiModelProperty(name = "creator", value = "网盘创建者", hidden = true)
    Boolean creator;

}
