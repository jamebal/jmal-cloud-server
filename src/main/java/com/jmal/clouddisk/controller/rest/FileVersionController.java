package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.service.IFileVersionService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * @author jmal
 * @Description 文件历史版本
 * @date 2023/5/10 16:56
 */
@Tag(name = "文件历史版本")
@Slf4j
@RequestMapping("history")
@RestController
@RequiredArgsConstructor
public class FileVersionController {

    private final IFileVersionService fileVersionService;

    @Operation(summary = "历史文件列表")
    @GetMapping("/list")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<GridFSBO>> list(@RequestParam String fileId, @RequestParam Integer pageSize, @RequestParam Integer pageIndex) {
        return fileVersionService.listFileVersion(fileId, pageSize, pageIndex);
    }

    @Operation(summary = "读取历史simText文件")
    @GetMapping("/preview/text")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> previewText(@RequestParam String id) {
        return ResultUtil.success(fileVersionService.getFileById(id));
    }

    @Operation(summary = "恢复该历史版本")
    @PutMapping("/recovery")
    @Permission("cloud:file:update")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    public ResponseResult<Object> recovery(@RequestParam String id) {
        fileVersionService.recovery(id);
        return ResultUtil.success();
    }

    @Operation(summary = "流式读取历史simText文件")
    @GetMapping("/preview/text/stream")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<StreamingResponseBody> previewTextStream(@RequestParam String id) {
        return new ResponseEntity<>(fileVersionService.getStreamFileById(id), HttpStatus.OK);
    }

}
