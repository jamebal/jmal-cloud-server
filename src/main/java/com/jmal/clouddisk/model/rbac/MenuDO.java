package com.jmal.clouddisk.model.rbac;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.service.impl.MenuService;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description 菜单模型
 * @Date 2021/1/7 8:53 下午
 */
@Getter
@Setter
@Document(collection = MenuService.COLLECTION_NAME)
@Entity
@Table(name = MenuService.COLLECTION_NAME)
public class MenuDO extends AuditableEntity implements Reflective {
    /***
     * 父级菜单Id
     */
    String parentId;
    /***
     * 菜单名称
     */
    String name;
    /***
     * 权限标识
     */
    String authority;
    /***
     * 路由地址
     */
    String path;
    /***
     * 组件路径
     */
    String component;
    /***
     * 菜单图标
     */
    String icon;
    /***
     * 排序号
     */
    Integer sortNumber;
    /***
     * 菜单类型 0:菜单，1:按钮
     */
    Integer menuType;
    /***
     * 是否隐藏
     */
    Boolean hide;
    /***
     * 创建时间
     */
    LocalDateTime createTime;
    /***
     * 修改时间
     */
    LocalDateTime updateTime;
}
