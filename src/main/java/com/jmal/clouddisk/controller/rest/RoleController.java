package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.query.QueryRoleDTO;
import com.jmal.clouddisk.model.rbac.RoleDTO;
import com.jmal.clouddisk.service.impl.RoleService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * @Description 角色管理
 * @blame jmal
 * @Date 2021/1/7 7:44 下午
 */
@RestController
@RequestMapping("role")
@Api(tags = "角色管理")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @ApiOperation(value = "角色列表")
    @GetMapping("/list")
    @Permission("sys:role:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<RoleDTO>> list(QueryRoleDTO queryDTO){
        return roleService.list(queryDTO);
    }

    @ApiOperation(value = "添加角色")
    @PostMapping("/add")
    @LogOperatingFun
    @Permission("sys:role:add")
    public ResponseResult<Object> add(@ModelAttribute @Validated RoleDTO roleDTO){
        return roleService.add(roleDTO);
    }

    @ApiOperation(value = "修改角色")
    @PutMapping("/update")
    @LogOperatingFun
    @Permission("sys:role:update")
    public ResponseResult<Object> update(@RequestBody @ModelAttribute @Validated RoleDTO roleDTO){
        if(roleDTO.getId() == null){
            return ResultUtil.warning("缺少参数roleId");
        }
        return roleService.update(roleDTO);
    }

    @ApiOperation(value = "删除角色")
    @DeleteMapping("/delete")
    @LogOperatingFun
    @Permission("sys:role:delete")
    public ResponseResult<Object> delete(@RequestParam String[] roleIds){
        roleService.delete(roleIds);
        return ResultUtil.success();
    }

}
