package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.SystemUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "版本检查")
public class AppCheckController {

    @GetMapping("/version")
    public ResponseResult<String> healthCheck() {
        return ResultUtil.success(SystemUtil.getNewVersion());
    }

}
