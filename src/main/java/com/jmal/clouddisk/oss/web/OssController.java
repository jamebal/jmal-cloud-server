package com.jmal.clouddisk.oss.web;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.oss.OssConfig;
import com.jmal.clouddisk.oss.PlatformOSS;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("oss")
@Tag(name = "oss Controller")
public class OssController {

    private final IOssWebService ossWebService;

    private final OssConfig ossConfig;


    public OssController(IOssWebService ossWebService, OssConfig ossConfig) {
        this.ossWebService = ossWebService;
        this.ossConfig = ossConfig;
    }

    @Operation(summary = "获取支持的平台的列表")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    @GetMapping("getPlatformList")
    public ResponseResult<List<Map<String, String>>> getPlatformList() {
        return ResultUtil.success(ossWebService.getPlatformList());
    }

    @Operation(summary = "获取appToken")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    @GetMapping("getAppToken")
    public ResponseResult<STSObjectVO> getAppToken(@RequestParam String platformKey) {
        return ossWebService.getAppToken();
    }

    @Operation(summary = "新增OSS配置")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    @PostMapping("addOssConfig")
    public ResponseResult<Object> addOssConfig(@Valid @RequestBody OssConfigDTO ossConfigDTO) {
        return ossConfig.addOssConfig(ossConfigDTO);
    }
}
