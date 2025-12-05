package com.jmal.clouddisk.oss.web;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.oss.PartInfo;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("oss")
@Tag(name = "OSS管理")
@RequiredArgsConstructor
public class OssController {

    private final OssConfigService ossConfigService;

    public final WebOssService webOssService;

    @Operation(summary = "获取支持的平台的列表")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    @GetMapping("getPlatformList")
    public ResponseResult<List<Map<String, String>>> getPlatformList() {
        return ResultUtil.success(webOssService.getPlatformList());
    }

    @Operation(summary = "OSS配置列表")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    @GetMapping("ossConfigList")
    @Permission(value = "cloud:oss:get")
    public ResponseResult<Object> ossConfigList() {
        return ossConfigService.ossConfigList();
    }

    @Operation(summary = "新增/修改OSS配置")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    @PutMapping("putOssConfig")
    @Permission(value = "cloud:oss:put")
    public ResponseResult<Object> putOssConfig(@Valid @RequestBody OssConfigDTO ossConfigDTO) {
        ossConfigService.putOssConfig(ossConfigDTO);
        return ResultUtil.success();
    }

    @Operation(summary = "删除OSS配置")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    @DeleteMapping("deleteOssConfig")
    @Permission(value = "cloud:oss:delete")
    public ResponseResult<Object> deleteOssConfig(@RequestParam String id) {
        return ossConfigService.deleteOssConfig(id);
    }

    @Operation(summary = "获取上传预签名URL")
    @GetMapping("/presign/upload")
    public ResponseResult<String> getUploadUrl(@RequestParam String objectName,
                                               @RequestParam(required = false) String contentType) {
        Result result = getResult(objectName);
        String url = result.ossService().getPresignedPutUrl(result.realityObjectName, contentType, 3600);
        return ResultUtil.success(url);
    }

    @Operation(summary = "获取下载预签名URL")
    @GetMapping("/presign/download")
    public ResponseResult<String> getDownloadUrl(@RequestParam String objectName) {
        Result result = getResult(objectName);
        String url = result.ossService().getPresignedObjectUrl(result.realityObjectName, 3600);
        return ResultUtil.success(url);
    }

    @Operation(summary = "初始化分片上传")
    @PostMapping("/presign/upload/multipart/init")
    public ResponseResult<String> initMultipartUpload(@RequestParam String objectName) {
        Result result = getResult(objectName);
        return ResultUtil.success(result.ossService().initiateMultipartUpload(result.realityObjectName));
    }

    @Operation(summary = "获取分片上传预签名URLs")
    @GetMapping("/presign/upload/multipart/presign-urls")
    public ResponseResult<Map<Integer, String>> getMultipartPresignUrls(@RequestParam String objectName, @RequestParam String uploadId, @RequestParam int totalParts) {
        Result result = getResult(objectName);
        Map<Integer, String> urls = result.ossService().getPresignedUploadPartUrls(result.realityObjectName, uploadId, totalParts, 3600);
        return ResultUtil.success(urls);
    }

    @Operation(summary = "完成分片上传")
    @PostMapping("/presign/upload/multipart/complete")
    public ResponseResult<Object> completeMultipartUpload(@RequestBody CompleteMultipartUploadRequest completeMultipartUploadRequest) {
        Result result = getResult(completeMultipartUploadRequest.getObjectName());
        result.ossService().completeMultipartUploadWithParts(result.realityObjectName, completeMultipartUploadRequest.getUploadId(), completeMultipartUploadRequest.getParts(), completeMultipartUploadRequest.getFileTotalSize());
        return ResultUtil.success();
    }

    @Data
    public static class CompleteMultipartUploadRequest {
        private String objectName;
        private String uploadId;
        private List<PartInfo> parts;
        private Long fileTotalSize;
    }

    @Operation(summary = "取消分片上传")
    @PostMapping("/presign/upload/multipart/abort")
    public ResponseResult<Object> abortMultipartUpload(@RequestParam String objectName, @RequestParam String uploadId) {
        Result result = getResult(objectName);
        result.ossService().abortMultipartUpload(result.realityObjectName, uploadId);
        return ResultUtil.success();
    }

    @NotNull
    private static Result getResult(String objectName) {
        Path prePath = Paths.get(objectName);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "无法获取文件的OSS存储路径");
        }
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String realityObjectName = WebOssService.getObjectName(prePath, ossPath, false);
        return new Result(ossService, realityObjectName);
    }

    private record Result(IOssService ossService, String realityObjectName) {}


}
