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
import org.springframework.core.io.InputStreamResource;
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
@RequestMapping("history")
@RestController
@RequiredArgsConstructor
public class FileVersionController {

    private final IFileVersionService fileVersionService;

    @Operation(summary = "历史文件列表(根据fileId)")
    @GetMapping("/list")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<GridFSBO>> list(String fileId, @RequestParam Integer pageSize, @RequestParam Integer pageIndex) {
        return fileVersionService.listFileVersion(fileId, pageSize, pageIndex);
    }

    @Operation(summary = "历史文件列表(根据filepath)")
    @GetMapping("/path/list")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<GridFSBO>> listByPath(@RequestParam String path, @RequestParam Integer pageSize, @RequestParam Integer pageIndex) {
        return fileVersionService.listFileVersionByPath(path, pageSize, pageIndex);
    }

    @Operation(summary = "读取历史文件")
    @GetMapping("/preview/file")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<InputStreamResource> readHistoryFile(@RequestParam String id) {
        return fileVersionService.readHistoryFile(id);
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
    public ResponseResult<Long> recovery(@RequestParam String id) {
        return ResultUtil.success(fileVersionService.recovery(id));
    }

    @Operation(summary = "删除该历史版本")
    @DeleteMapping("/delete")
    @Permission("cloud:file:delete")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    public ResponseResult<Object> delete(@RequestParam String id) {
        fileVersionService.deleteOne(id);
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
