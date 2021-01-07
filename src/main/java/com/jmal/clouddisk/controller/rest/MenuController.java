package com.jmal.clouddisk.controller.rest;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.CategoryDO;
import com.jmal.clouddisk.model.CategoryDTO;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.model.rbac.MenuDTO;
import com.jmal.clouddisk.model.rbac.RoleDTO;
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

    @ApiOperation(value = "菜单列表")
    @PostMapping("/list")
    public ResponseResult<List<MenuDTO>> list(String parentId){
        return ResultUtil.success(menuService.list(parentId));
    }

    @ApiOperation("菜单树")
    @GetMapping("/tree")
    public ResponseResult<List<MenuDTO>> tree(String userId) {
        return ResultUtil.success(menuService.tree());
    }

    @ApiOperation("菜单信息")
    @GetMapping("/info")
    @ResponseBody
    public ResponseResult<MenuDTO> info(@RequestParam String menuId) {
        MenuDTO menuDTO = new MenuDTO();
        MenuDO menuDO = menuService.getMenuInfo(menuId);
        if(menuDO != null){
            CglibUtil.copy(menuDO, menuDTO);
        }
        return ResultUtil.success(menuDTO);
    }

    @ApiOperation("添加菜单")
    @PostMapping("/add")
    public ResponseResult<Object> add(@ModelAttribute @Validated MenuDTO menuDTO) {
        return menuService.add(menuDTO);
    }

    @ApiOperation("更新菜单")
    @PutMapping("/update")
    @ResponseBody
    public ResponseResult<Object> update(@ModelAttribute MenuDTO menuDTO) {
        if(menuDTO.getId() == null){
            return ResultUtil.warning("缺少参数menuId");
        }
        return menuService.update(menuDTO);
    }

    @ApiOperation("删除菜单")
    @DeleteMapping("/delete")
    public ResponseResult<Object> delete(@RequestParam String[] menuIds) {
        List<String> categoryList = Arrays.asList(menuIds);
        menuService.delete(categoryList);
        return ResultUtil.success();
    }


}
