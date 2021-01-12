package com.jmal.clouddisk.controller.rest;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.CategoryDO;
import com.jmal.clouddisk.model.CategoryDTO;
import com.jmal.clouddisk.service.impl.CategoryService;
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
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:47 下午
 */
@RestController
@Api(tags = "分类管理")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @ApiOperation("分类列表")
    @GetMapping("/category/list")
    @Permission("website:set:list")
    public ResponseResult<List<CategoryDTO>> list(String userId, String parentCategoryId) {
        return ResultUtil.success(categoryService.list(userId, parentCategoryId));
    }

    @ApiOperation("分类树")
    @GetMapping("/category/tree")
    @Permission("website:set:list")
    public ResponseResult<List<CategoryDTO>> tree(String userId) {
        return ResultUtil.success(categoryService.tree(userId, false));
    }

    @ApiOperation("分类信息")
    @GetMapping("/category/info")
    @ResponseBody
    @Permission("website:set:list")
    public ResponseResult<CategoryDTO> categoryInfo(@RequestParam String categoryId) {
        CategoryDTO categoryDTO = new CategoryDTO();
        CategoryDO categoryDO = categoryService.getCategoryInfo(categoryId);
        if(categoryDO != null){
            CglibUtil.copy(categoryDO, categoryDTO);
        }
        return ResultUtil.success(categoryDTO);
    }

    @ApiOperation("添加分类")
    @PostMapping("/category/add")
    @Permission("website:set:add")
    public ResponseResult<Object> add(@ModelAttribute @Validated CategoryDTO categoryDTO) {
        return categoryService.add(categoryDTO);
    }

    @ApiOperation("更新分类")
    @PutMapping("/category/update")
    @ResponseBody
    @Permission("website:set:update")
    public ResponseResult<Object> update(@ModelAttribute CategoryDTO categoryDTO) {
        return categoryService.update(categoryDTO);
    }

    @ApiOperation("设置默认分类")
    @PutMapping("/category/setDefault")
    @Permission("website:set:update")
    public ResponseResult<Object> setDefault(@RequestParam String categoryId) {
        return categoryService.setDefault(categoryId);
    }

    @ApiOperation("删除分类")
    @DeleteMapping("/category/delete")
    @Permission("website:set:delete")
    public ResponseResult<Object> delete(@RequestParam String[] categoryIds) {
        List<String> categoryList = Arrays.asList(categoryIds);
        categoryService.delete(categoryList);
        return ResultUtil.success();
    }
}
