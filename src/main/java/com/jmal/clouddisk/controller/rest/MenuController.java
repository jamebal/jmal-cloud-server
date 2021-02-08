package com.jmal.clouddisk.controller.rest;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.annotation.AnnoManageUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.query.QueryMenuDTO;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.model.rbac.MenuDTO;
import com.jmal.clouddisk.service.impl.MenuService;
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
 * @Description 菜单管理
 * @blame jmal
 * @Date 2021/1/7 7:44 下午
 */
@RestController
@RequestMapping("menu")
@Api(tags = "菜单管理")
public class MenuController {

    @Autowired
    private MenuService menuService;

    @ApiOperation("菜单树")
    @GetMapping("/tree")
    @Permission("sys:user:list")
    @LogOperatingFun
    public ResponseResult<List<MenuDTO>> tree(QueryMenuDTO queryDTO) {
        return ResultUtil.success(menuService.tree(queryDTO));
    }

    @ApiOperation("菜单信息")
    @GetMapping("/info")
    @ResponseBody
    @Permission("sys:menu:list")
    @LogOperatingFun
    public ResponseResult<MenuDTO> info(@RequestParam String menuId) {
        MenuDTO menuDTO = new MenuDTO();
        MenuDO menuDO = menuService.getMenuInfo(menuId);
        if(menuDO != null){
            CglibUtil.copy(menuDO, menuDTO);
        }
        return ResultUtil.success(menuDTO);
    }

    @ApiOperation("权限标识列表")
    @GetMapping("/authorities")
    @ResponseBody
    @LogOperatingFun
    @Permission("sys:menu:list")
    public ResponseResult<List<String>> authorities() {
        return ResultUtil.success(AnnoManageUtil.AUTHORITIES);
    }

    @ApiOperation("添加菜单")
    @PostMapping("/add")
    @LogOperatingFun
    @Permission("sys:menu:add")
    public ResponseResult<Object> add(@ModelAttribute @Validated MenuDTO menuDTO) {
        return menuService.add(menuDTO);
    }

    @ApiOperation("更新菜单")
    @PutMapping("/update")
    @ResponseBody
    @LogOperatingFun
    @Permission("sys:menu:update")
    public ResponseResult<Object> update(@ModelAttribute MenuDTO menuDTO) {
        if(menuDTO.getId() == null){
            return ResultUtil.warning("缺少参数menuId");
        }
        return menuService.update(menuDTO);
    }

    @ApiOperation("删除菜单")
    @DeleteMapping("/delete")
    @LogOperatingFun
    @Permission("sys:menu:delete")
    public ResponseResult<Object> delete(@RequestParam String[] menuIds) {
        List<String> categoryList = Arrays.asList(menuIds);
        menuService.delete(categoryList);
        return ResultUtil.success();
    }


}
