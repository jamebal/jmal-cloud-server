package com.jmal.clouddisk.controller.rest;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.Category;
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
import java.util.Map;

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
    public ResponseResult<List<CategoryDTO>> list(@RequestParam String userId, String parentCategoryId) {
        return ResultUtil.success(categoryService.list(userId, parentCategoryId));
    }

    @ApiOperation("分类树")
    @GetMapping("/category/tree")
    public ResponseResult<List<Map<String, Object>>> tree(@RequestParam String userId) {
        return ResultUtil.success(categoryService.tree(userId));
    }

    @ApiOperation("分类信息")
    @GetMapping("/category/info")
    @ResponseBody
    public ResponseResult<CategoryDTO> categoryInfo(@RequestParam String categoryId) {
        CategoryDTO categoryDTO = new CategoryDTO();
        Category category = categoryService.getCategoryInfo(categoryId);
        if(category != null){
            CglibUtil.copy(category, categoryDTO);
        }
        return ResultUtil.success(categoryDTO);
    }

    @ApiOperation("添加分类")
    @PostMapping("/category/add")
    public ResponseResult<Object> add(@ModelAttribute @Validated CategoryDTO categoryDTO) {
        categoryDTO.setId(null);
        return categoryService.add(categoryDTO);
    }

    @ApiOperation("更新分类")
    @PutMapping("/category/update")
    @ResponseBody
    public ResponseResult<Object> update(@ModelAttribute CategoryDTO categoryDTO) {
        return categoryService.update(categoryDTO);
    }

    @ApiOperation("设置默认分类")
    @PutMapping("/category/setDefault")
    public ResponseResult<Object> setDefault(@RequestParam String categoryId) {
        return categoryService.setDefault(categoryId);
    }

    @ApiOperation("删除分类")
    @DeleteMapping("/category/delete")
    public ResponseResult<Object> delete(@RequestParam String[] categoryIds) {
        List<String> categoryList = Arrays.asList(categoryIds);
        categoryService.delete(categoryList);
        return ResultUtil.success();
    }
}
