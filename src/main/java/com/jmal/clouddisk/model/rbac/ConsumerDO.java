package com.jmal.clouddisk.model.rbac;

import com.jmal.clouddisk.service.impl.UserServiceImpl;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 用户模型
 * @author jmal
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema
@Document(collection = UserServiceImpl.COLLECTION_NAME)
public class ConsumerDO extends ConsumerBase {
    String id;
    @Schema(name = "username", title = "用户名", example = "admin")
    @Indexed
    String username;
    @Indexed
    @Schema(name = "showName", title = "显示用户名", example = "管理员1")
    String showName;
    @Schema(name = "password", title = "密码", example = "123456")
    String password;
    @Schema(hidden = true, title = "密码加密后的字符串")
    String encryptPwd;
    @Schema(title = "头像", example = "https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif")
    String avatar;
    @Schema(name = "slogan", title = "标语")
    String slogan;
    @Schema(name = "introduction", title = "简介")
    String introduction;
    @Schema(name = "webpDisabled", title = "是否禁用webp")
    Boolean webpDisabled;
    @Schema(name = "roles", title = "角色Id集合")
    List<String> roles;
    @Schema(name = "quota", title = "默认配额, 10G", example = "10")
    Integer quota;
    @Schema(name = "takeUpSpace", title = "已使用的空间")
    Long takeUpSpace;
    @Schema(name = "createTime", title = "创建时间")
    LocalDateTime createTime;
    @Schema(name = "updateTime", title = "修改时间", hidden = true)
    LocalDateTime updateTime;
    @Schema(name = "creator", title = "网盘创建者", hidden = true)
    Boolean creator;

}
