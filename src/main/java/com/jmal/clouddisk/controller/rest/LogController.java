package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author jmal
 * @Description 日志管理
 * @Date 2021/2/8 2:12 下午
 */
@RestController
@RequestMapping("log")
@Tag(name = "日志")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @Operation(summary = "日志查询")
    @GetMapping("/list")
    @Permission("sys:log:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<LogOperation>> list(@ModelAttribute LogOperationDTO logOperationDTO){
        return logService.list(logOperationDTO);
    }

}
