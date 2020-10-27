package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.model.CategoryDTO;
import com.jmal.clouddisk.service.impl.CategoryService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:47 下午
 */
@Controller
@Api(tags = "分类管理")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @ApiOperation("分类列表")
    @GetMapping("/category/list")
    @ResponseBody
    public ResponseResult list(@RequestParam String userId) {
        return categoryService.list(userId);
    }

    @ApiOperation("分类树")
    @GetMapping("/category/tree")
    @ResponseBody
    public ResponseResult tree(@RequestParam String userId) {
        return categoryService.tree(userId);
    }

    @ApiOperation("分类信息")
    @GetMapping("/category/info")
    @ResponseBody
    public ResponseResult categoryInfo(@RequestParam String userId, @RequestParam String categoryName) {
        return ResultUtil.success(categoryService.categoryInfo(userId, categoryName));
    }

    @ApiOperation("添加分类")
    @PostMapping("/category/add")
    @ResponseBody
    public ResponseResult add(@ModelAttribute CategoryDTO categoryDTO) {
        return categoryService.add(categoryDTO);
    }

    @ApiOperation("更新分类")
    @PostMapping("/category/update")
    @ResponseBody
    public ResponseResult update(@ModelAttribute CategoryDTO categoryDTO) {
        return categoryService.update(categoryDTO);
    }

    @ApiOperation("删除分类")
    @PostMapping("/category/delete")
    @ResponseBody
    public ResponseResult delete(@RequestParam String userId, @RequestParam String categoryName) {
        return categoryService.delete(userId, categoryName);
    }
}
