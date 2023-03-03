package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.AnnoManageUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.query.QueryMenuDTO;
import com.jmal.clouddisk.model.rbac.MenuDO;
import com.jmal.clouddisk.model.rbac.MenuDTO;
import com.jmal.clouddisk.service.impl.MenuService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * @author jmal
 * @Description 菜单管理
 * @Date 2021/1/7 7:44 下午
 */
@RestController
@RequestMapping("menu")
@Tag(name = "菜单管理")
public class MenuController {

    @Autowired
    private MenuService menuService;

    @Operation(summary = "菜单树")
    @GetMapping("/tree")
    @Permission("sys:user:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<MenuDTO>> tree(QueryMenuDTO queryDTO) {
        return ResultUtil.success(menuService.tree(queryDTO));
    }

    @Operation(summary = "菜单信息")
    @GetMapping("/info")
    @ResponseBody
    @Permission("sys:menu:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<MenuDTO> info(@RequestParam String menuId) {
        MenuDTO menuDTO = new MenuDTO();
        MenuDO menuDO = menuService.getMenuInfo(menuId);
        if(menuDO != null){
            BeanUtils.copyProperties(menuDO, menuDTO);
        }
        return ResultUtil.success(menuDTO);
    }

    @Operation(summary = "权限标识列表")
    @GetMapping("/authorities")
    @ResponseBody
    @Permission("sys:menu:list")
    public ResponseResult<List<String>> authorities() {
        return ResultUtil.success(AnnoManageUtil.AUTHORITIES);
    }

    @Operation(summary = "添加菜单")
    @PostMapping("/add")
    @LogOperatingFun
    @Permission("sys:menu:add")
    public ResponseResult<Object> add(@ModelAttribute @Validated MenuDTO menuDTO) {
        return menuService.add(menuDTO);
    }

    @Operation(summary = "更新菜单")
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

    @Operation(summary = "删除菜单")
    @DeleteMapping("/delete")
    @LogOperatingFun
    @Permission("sys:menu:delete")
    public ResponseResult<Object> delete(@RequestParam String[] menuIds) {
        List<String> categoryList = Arrays.asList(menuIds);
        menuService.delete(categoryList);
        return ResultUtil.success();
    }


}
