package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author jmal
 * @Description 日志管理
 * @Date 2021/2/8 2:12 下午
 */
@RestController
@RequestMapping("log")
@Tag(name = "日志")
public class LogController {

    @Autowired
    private LogService logService;

    @Operation(summary = "日志查询")
    @GetMapping("/list")
    @Permission("sys:log:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<LogOperation>> list(@ModelAttribute LogOperationDTO logOperationDTO){
        return logService.list(logOperationDTO);
    }

}
