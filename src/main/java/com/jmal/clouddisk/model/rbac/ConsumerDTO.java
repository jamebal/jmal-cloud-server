package com.jmal.clouddisk.model.rbac;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.service.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 用户模型传输对象
 * @author jmal
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema
@Valid
public class ConsumerDTO extends ConsumerBase implements Reflective {
    @Id
    String id;
    @NotNull(message = "用户账号不能为空")
    @Schema(name = "username", title = "用户账号", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    String username;
    @NotNull(message = "用户名不能为空")
    @Schema(name = "showName", title = "用户名", example = "管理员1", requiredMode = Schema.RequiredMode.REQUIRED)
    String showName;
    @Schema(name = "avatar", title = "头像")
    String avatar;
    @Schema(name = "slogan", title = "标语")
    String slogan;
    @Schema(name = "introduction", title = "简介")
    String introduction;
    @Schema(name = "webpDisabled", title = "是否禁用webp")
    Boolean webpDisabled;
    @Schema(name = "roles", title = "角色Id集合")
    List<String> roles;
    @Schema(name = "roleList", title = "角色集合", hidden = true)
    List<RoleDTO> roleList;
    @Max(value = 1073741824, message = "配额过大")
    @Schema(name = "quota", title = "默认配额, 10G", example = "10")
    Integer quota;
    @Schema(name = "takeUpSpace", title = "已使用的空间")
    Long takeUpSpace;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(name = Constants.CREATE_TIME, title = "创建时间", hidden = true)
    LocalDateTime createTime;
    @Schema(name = "netdiskLogo", title = "网盘logo文件名", hidden = true)
    String netdiskLogo;
    @Schema(name = "netdiskName", title = "网盘名称", hidden = true)
    String netdiskName;

    @Schema(name = "rememberMe", title = "记住我", hidden = true)
    Boolean rememberMe;

    @Schema(name = "newVersion", title = "新版本", hidden = true)
    String newVersion;

    @Schema(name = "iframe", title = "iframe", hidden = true)
    String iframe;

    @Schema(name = "exactSearch", title = "exactSearch", hidden = true)
    Boolean exactSearch;

    @Schema(name = "mfaToken", title = "mfaToken", hidden = true)
    String mfaToken;

    @Schema(name = "mfaCode", title = "mfaCode", hidden = true)
    String mfaCode;

    @Schema(name = "mfaSecret", title = "mfaCode", hidden = true)
    String mfaSecret;

    Personalization personalization;

}
