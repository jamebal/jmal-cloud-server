package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.service.impl.DirectLinkService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "直链管理")
@RequestMapping("direct-link")
@RequiredArgsConstructor
public class DirectLinkController {

    private final DirectLinkService directLinkService;

    @Operation(summary = "创建直链")
    @PostMapping("/create")
    @Permission(value = "cloud:file:upload")
    @LogOperatingFun
    public ResponseResult<String> createDirectLink(@RequestParam String fileId) {
        return ResultUtil.success(directLinkService.createDirectLink(fileId));
    }

    @Operation(summary = "重置直链")
    @PutMapping("/reset")
    @Permission(value = "cloud:file:upload")
    @LogOperatingFun
    public ResponseResult<String> resetDirectLink(@RequestParam String fileId) {
        return ResultUtil.success(directLinkService.resetDirectLink(fileId));
    }

    @Operation(summary = "重置所有直链")
    @DeleteMapping("/reset-all")
    @Permission(value = "cloud:file:upload")
    @LogOperatingFun
    public ResponseResult<String> resetAllDirectLink(@RequestParam String fileId) {
        return ResultUtil.success(directLinkService.resetAllDirectLink(fileId));
    }

}
