package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.service.impl.CateGoryService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:47 下午
 */
@Controller
@Api(tags = "分类管理")
@RestController("/category")
public class CateGoryController {

    CateGoryService cateGoryService;

    @ApiOperation("分类列表")
    @GetMapping("/list")
    @ResponseBody
    public ResponseResult list(@RequestParam String userId) {
        return cateGoryService.list(userId);
    }
}
