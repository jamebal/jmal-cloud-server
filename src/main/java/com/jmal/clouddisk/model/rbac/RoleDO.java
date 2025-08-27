package com.jmal.clouddisk.model.rbac;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.service.impl.RoleService;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 角色模型
 * @blame jmal
 * @Date 2021/1/7 7:41 下午
 */
@Getter
@Setter
@Document(collection = RoleService.COLLECTION_NAME)
@Entity
@Table(name = RoleService.COLLECTION_NAME)
public class RoleDO extends AuditableEntity implements Reflective {
    /***
     * 角色名称
     */
    String name;
    /***
     * 角色标识
     */
    String code;
    /***
     * 备注
     */
    String remarks;
    /***
     * 菜单Id列表
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_menu_ids",
            joinColumns = @JoinColumn(name = "role_id")
    )
    @Column(name = "menu_id", length = 24)
    List<String> menuIds;
    /***
     * 创建时间
     */
    LocalDateTime createTime;
    /***
     * 修改时间
     */
    LocalDateTime updateTime;
}
