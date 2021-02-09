package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author jmal
 * @Description 日志管理
 * @Date 2021/2/8 2:12 下午
 */
@RestController
@RequestMapping("log")
@Api(tags = "日志")
public class LogController {

    @Autowired
    private LogService logService;

    @ApiOperation("日志查询")
    @GetMapping("/list")
    @Permission("sys:log:list")
    public ResponseResult<List<LogOperation>> list(@ModelAttribute LogOperationDTO logOperationDTO){
        return logService.list(logOperationDTO);
    }

}
