package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @Operation(summary = "查询日志")
    @GetMapping("/list")
    @Permission("sys:log:list")
    public ResponseResult<List<LogOperation>> list(@ModelAttribute LogOperationDTO logOperationDTO) {
        return logService.list(logOperationDTO);
    }

    @Operation(summary = "查询文件操作记录")
    @GetMapping("/getFileOperationHistory")
    @Permission("sys:log:list")
    public ResponseResult<List<LogOperationDTO>> getFileOperationHistory(@ModelAttribute LogOperationDTO logOperationDTO, @RequestParam String fileId) {
        return logService.getFileOperationHistory(logOperationDTO, fileId);
    }

}
