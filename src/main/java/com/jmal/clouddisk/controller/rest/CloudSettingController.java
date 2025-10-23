package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.lucene.RebuildIndexTaskService;
import com.jmal.clouddisk.lucene.TaskProgress;
import com.jmal.clouddisk.lucene.TaskProgressService;
import com.jmal.clouddisk.media.TranscodeConfig;
import com.jmal.clouddisk.media.VideoProcessService;
import com.jmal.clouddisk.model.LdapConfigDTO;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.WebsiteSettingDTO;
import com.jmal.clouddisk.ocr.OcrConfig;
import com.jmal.clouddisk.ocr.OcrService;
import com.jmal.clouddisk.service.IAuthService;
import com.jmal.clouddisk.service.impl.CommonUserFileService;
import com.jmal.clouddisk.service.impl.SettingService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "网盘设置")
@RequiredArgsConstructor
public class CloudSettingController {

    private final SettingService settingService;

    private final TaskProgressService taskProgressService;

    private final CommonUserFileService commonUserFileService;

    private final IAuthService authService;

    private final RebuildIndexTaskService rebuildIndexTaskService;

    private final VideoProcessService videoProcessService;

    private final OcrService ocrService;

    @Operation(summary = "重建索引-用户")
    @GetMapping("/user/setting/sync")
    @Permission(value = "cloud:file:upload")
    @LogOperatingFun
    public ResponseResult<Object> userSync(@RequestParam String username, String path) {
        return rebuildIndexTaskService.sync(username, path, false);
    }

    @Operation(summary = "重建索引-全盘")
    @GetMapping("/cloud/setting/sync")
    @Permission(value = "cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> sync(@RequestParam String username) {
        return rebuildIndexTaskService.sync(username, null, true);
    }

    @Operation(summary = "重新计算文件夹大小")
    @GetMapping("/cloud/setting/recalculateFolderSize")
    @Permission(value = "cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> recalculateFolderSize() {
        return settingService.recalculateFolderSize();
    }

    @Operation(summary = "获取视频转码配置")
    @GetMapping("/cloud/setting/transcode/config")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<TranscodeConfig> getTranscodeConfig() {
        return ResultUtil.success(videoProcessService.getTranscodeConfig());
    }

    @Operation(summary = "修改视频转码配置")
    @PutMapping("/cloud/setting/transcode/config")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<Object> setTranscodeConfig(@RequestBody @Validated TranscodeConfig transcodeConfig) {
        return ResultUtil.success(videoProcessService.setTranscodeConfig(transcodeConfig));
    }

    @Operation(summary = "获取ocr配置")
    @GetMapping("/cloud/setting/ocr/config")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<OcrConfig> getOcrConfig() {
        return ResultUtil.success(ocrService.getOcrConfig());
    }

    @Operation(summary = "修改ocr配置")
    @PutMapping("/cloud/setting/ocr/config")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<Object> setOcrConfig(@RequestBody @Validated OcrConfig ocrConfig) {
        return ResultUtil.success(ocrService.setOcrConfig(ocrConfig));
    }

    @Operation(summary = "取消转码任务")
    @PutMapping("/cloud/setting/transcode/cancel-task")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<Object> cancelTranscodeTask() {
        videoProcessService.cancelTranscodeTask();
        return ResultUtil.success();
    }

    @Operation(summary = "是否正在同步")
    @GetMapping("/user/setting/isSync")
    public ResponseResult<Map<String, Double>> isSync() {
        return ResultUtil.success(rebuildIndexTaskService.isSync());
    }

    @Operation(summary = "上传网盘logo")
    @PostMapping("/user/setting/upload_logo")
    @Permission(value = "cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> uploadLogo(MultipartFile file) {
        return settingService.uploadLogo(file);
    }

    @Operation(summary = "修改网盘名称")
    @PutMapping("/user/setting/update_netdisk_name")
    @Permission(value = "cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> updateNetdiskName(@RequestParam String netdiskName) {
        return settingService.updateNetdiskName(netdiskName);
    }

    @Operation(summary = "重置角色菜单")
    @PutMapping("/user/setting/resetMenuAndRole")
    @Permission(onlyCreator = true)
    @LogOperatingFun
    public ResponseResult<Object> resetMenuAndRole() {
        settingService.resetMenuAndRole();
        return ResultUtil.success();
    }

    @Operation(summary = "获取是否禁用webp状态")
    @GetMapping("/user/setting/get/webp")
    @Permission("sys:user:list")
    public ResponseResult<Boolean> getDisabledWebp(@RequestParam String userId) {
        return ResultUtil.success(commonUserFileService.getDisabledWebp(userId));
    }

    @Operation(summary = "加载ldap配置")
    @GetMapping("/ldap/config")
    public ResponseResult<Object> loadLdapConfig() {
        return ResultUtil.success(authService.loadLdapConfig());
    }

    @Operation(summary = "ldap配置")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    @PutMapping("/ldap/config")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<Object> updateLdapConfig(@RequestBody LdapConfigDTO ldapConfigDTO) {
        return authService.updateLdapConfig(ldapConfigDTO);
    }

    @Operation(summary = "测试ldap配置")
    @PutMapping("/ldap/test-config")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<Object> testLdapConfig(@RequestBody LdapConfigDTO ldapConfigDTO) {
        authService.testLdapConfig(ldapConfigDTO);
        return ResultUtil.success();
    }

    @Operation(summary = "获取预览配置")
    @GetMapping("/cloud/setting/preview/config")
    public ResponseResult<Object> getPreviewConfig() {
        return ResultUtil.success(settingService.getPreviewConfig());
    }

    @Operation(summary = "更新预览配置")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    @PutMapping("/cloud/setting/preview/config")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<Object> updatePreviewConfig(@RequestBody WebsiteSettingDTO websiteSettingDTO) {
        settingService.updatePreviewConfig(websiteSettingDTO);
        return ResultUtil.success();
    }

    @Operation(summary = "加载任务进度")
    @GetMapping("/cloud/task/progress")
    @Permission(value = "cloud:file:upload")
    public ResponseResult<List<TaskProgress>> getTaskProgress() {
        return ResultUtil.success(taskProgressService.getTaskProgressList());
    }

    @Operation(summary = "加载转码状态")
    @GetMapping("/cloud/transcode/status")
    @Permission(value = "cloud:file:upload")
    public ResponseResult<Map<String, Integer>> getTranscodeStatus() {
        return ResultUtil.success(videoProcessService.getTranscodeStatus());
    }

    @Operation(summary = "获取是否强制启用两步认证")
    @GetMapping("/cloud/setting/mfa-force-enable")
    public ResponseResult<Object> getMfaForceEnable() {
        return ResultUtil.success(settingService.getMfaForceEnable());
    }

    @Operation(summary = "设置是否强制启用两步认证")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    @PutMapping("/cloud/setting/mfa-force-enable")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<Object> setMfaForceEnable(@RequestParam Boolean mfaForceEnable) {
        settingService.setMfaForceEnable(mfaForceEnable);
        return ResultUtil.success();
    }

}
