package com.jmal.clouddisk.model.rbac;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description 角色模型
 * @blame jmal
 * @Date 2021/1/7 7:41 下午
 */
@Data
public class RoleDO {
    /***
     * 主键
     */
    @Id
    String id;
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
