package com.jmal.clouddisk.oss.web;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("oss")
@Tag(name = "oss Controller")
public class OssController {

    private final IOssWebService ossWebService;

    private final OssConfigService ossConfigService;


    public OssController(IOssWebService ossWebService, OssConfigService ossConfigService) {
        this.ossWebService = ossWebService;
        this.ossConfigService = ossConfigService;
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

    @Operation(summary = "新增/修改OSS配置")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    @PutMapping("putOssConfig")
    public ResponseResult<Object> putOssConfig(@Valid @RequestBody OssConfigDTO ossConfigDTO) {
        return ossConfigService.putOssConfig(ossConfigDTO);
    }

    @Operation(summary = "判断目录是否存在")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    @GetMapping("existFolderName")
    public ResponseResult<Boolean> existFolderName(@RequestParam String username, @RequestParam String folderName) {
        return ossConfigService.existFolderName(username, folderName);
    }
}
