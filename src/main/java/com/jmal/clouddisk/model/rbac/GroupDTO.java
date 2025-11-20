package com.jmal.clouddisk.model.rbac;

import com.jmal.clouddisk.config.Reflective;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * @Description 用户组模型
 * @author jmal
 */
@Data
@Schema
@Valid
public class GroupDTO implements Reflective {

    public String id;

    @NotNull(message = "组标识不能为空")
    @Size(max = 32, message = "组标识过长")
    @Schema(name = "code", title = "组标识", example = "dev_group")
    @Column(length = 32, unique = true, nullable = false)
    private String code;

    @NotNull(message = "组名不能为空")
    @Size(max = 64, message = "组名过长")
    @Schema(name = "name", title = "组名", example = "开发组")
    @Column(length = 64, nullable = false)
    private String name;

    @Schema(name = "description", title = "描述", example = "开发人员用户组")
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 角色ID列表
     * 组内所有用户继承这些角色
     */
    @Schema(name = "roles", title = "角色ID列表")
    @Column(name = "roles")
    @JdbcTypeCode(SqlTypes.JSON)
    List<String> roles;

}
