package com.jmal.clouddisk.model.rbac;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableTimeEntity;
import com.jmal.clouddisk.service.impl.GroupService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * @Description 用户组模型
 * @author jmal
 */
@Getter
@Setter
@Schema(description = "用户组")
@Document(collection = GroupService.COLLECTION_NAME)
@Entity
@Table(name = GroupService.COLLECTION_NAME)
public class GroupDO extends AuditableTimeEntity implements Reflective {

    @Schema(name = "code", title = "组标识", example = "dev_group")
    @Column(length = 32, unique = true, nullable = false)
    private String code;

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
