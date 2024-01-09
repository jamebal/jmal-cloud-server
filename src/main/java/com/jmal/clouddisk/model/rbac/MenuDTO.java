package com.jmal.clouddisk.model.rbac;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 菜单传输对象
 * @blame jmal
 * @Date 2021/1/7 8:53 下午
 */
@Data
@Valid
@Schema
public class MenuDTO implements Comparable<MenuDTO>{
    /***
     * 主键
     */
    String id;
    @Schema(name = "parentId", title = "父级菜单Id")
    String parentId;
    @NotNull(message = "菜单名称不能为空")
    @Schema(name = "name", title = "菜单名称", required = true)
    String name;
    @Schema(name = "authority", title = "权限标识")
    String authority;
    @Schema(name = "path", title = "路由地址")
    String path;
    @Schema(name = "component", title = "组件路径")
    String component;
    @Schema(name = "icon", title = "菜单图标")
    String icon;
    @NotNull(message = "排序号不能为空")
    @Schema(name = "sortNumber", title = "排序号", required = true)
    Integer sortNumber;
    @NotNull(message = "菜单类型不能为空")
    @Schema(name = "menuType", title = "菜单类型 0:菜单，1:按钮", required = true)
    Integer menuType;
    @NotNull(message = "是否隐藏不能为空")
    @Schema(name = "hide", title = "是否隐藏", required = true)
    Boolean hide;
    /***
     * 子菜单
     */
    @Schema(hidden = true)
    private List<MenuDTO> children;
    /***
     * 角色是否拥有该菜单
     */
    @Schema(hidden = true)
    private Boolean checked;
    /***
     * 创建时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(hidden = true)
    LocalDateTime createTime;
    /***
     * 修改时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(hidden = true)
    LocalDateTime updateTime;

    @Override
    public int compareTo(MenuDTO o) {
        return this.getSortNumber().compareTo(o.getSortNumber());
    }
}
