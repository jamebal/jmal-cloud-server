package com.jmal.clouddisk.office;

import com.alibaba.fastjson.JSON;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.office.callbacks.CallbackHandler;
import com.jmal.clouddisk.office.model.OfficeConfigDTO;
import com.jmal.clouddisk.office.model.Track;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.ShareServiceImpl;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @author jmal
 * @Description office文件管理
 * @date 2022/8/11 16:29
 */
@Tag(name = "office文件管理")
@Slf4j
@RestController
@RequiredArgsConstructor
public class OfficeController {

    private final CallbackHandler callbackHandler;

    private final ShareServiceImpl shareService;

    private final OfficeConfigService officeConfigService;

    @Operation(summary = "office 回调")
    @PostMapping("/office/track")
    @Permission("cloud:file:list")
    public String track(@RequestParam(Constants.FILE_ID) String fileId, @RequestBody Track body) {
        body.setFileId(fileId);
        log.info("callbackHandler, {}", JSON.toJSONString(body));
        int error = callbackHandler.handle(body);
        return "{\"error\":" + error + "}";
    }

    @Operation(summary = "signature")
    @PostMapping("/office/signature")
    public ResponseResult<String> signature(@RequestBody Map<String, Object> config) {
        return ResultUtil.success(officeConfigService.createOfficeToken(config));
    }

    @Operation(summary = "public signature")
    @PostMapping("/public/office/signature")
    public ResponseResult<String> signaturePublic(@RequestBody Map<String, Object> config, @RequestParam String shareId, @RequestParam String shareToken) {
        shareService.validShare(shareToken, shareId);
        return ResultUtil.success(officeConfigService.createOfficeToken(config));
    }

    @Operation(summary = "获取office配置")
    @GetMapping("/office/config")
    @Permission(value = "cloud:file:list")
    public ResponseResult<OfficeConfigDTO> getOfficeConfig() {
        return ResultUtil.success(officeConfigService.getOfficeConfig());
    }

    @Operation(summary = "更新office配置")
    @PutMapping("/office/config")
    @Permission(value = "cloud:set:sync")
    public ResponseResult<OfficeConfigDTO> putOfficeConfig(@RequestBody @Validated OfficeConfigDTO officeConfigDTO) {
        officeConfigService.setOfficeConfig(officeConfigDTO);
        return ResultUtil.success();
    }

}
