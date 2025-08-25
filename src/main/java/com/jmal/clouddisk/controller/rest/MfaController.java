package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.controller.record.MfaSetupResponse;
import com.jmal.clouddisk.controller.record.MfaVerifyRequest;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.SettingService;
import com.jmal.clouddisk.service.impl.TotpService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.MessageUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "两步验证")
public class MfaController {

    private final TotpService totpService;
    private final IUserService userService;
    private final SettingService settingService;
    private final UserLoginHolder userLoginHolder;
    private final MessageUtil messageUtil;

    @GetMapping("/public/mfa/setup")
    @Operation(summary = "准备开启两步验证")
    public ResponseResult<Object> initiateMfaSetup(@RequestParam String username) {
        if (userService.isMfaEnabled(username)) {
            return ResultUtil.success(new MfaSetupResponse(true, null, null, settingService.getMfaForceEnable()));
        }
        final String secret = totpService.generateNewSecret();
        final String qrCodeUri = totpService.generateQrCodeImageUri(secret, username);
        return ResultUtil.success(new MfaSetupResponse(false, secret, qrCodeUri, null));
    }

    @PostMapping("/mfa/enable")
    @Permission("sys:user:update")
    @Operation(summary = "验证并开启两步验证")
    public ResponseResult<Object> verifyAndEnableMfa(@RequestBody MfaVerifyRequest request) {
        if (userService.isMfaEnabled(userLoginHolder.getUsername())) {
            throw new CommonException(ExceptionType.EXISTING_RESOURCES.getCode(), "MFA已启用");
        }

        // 使用前端传回的secret和用户输入的code进行验证
        if (!totpService.isRawCodeValid(request.secret(), request.code())) {
            return ResultUtil.error(messageUtil.getMessage("login.mfaError"));
        }

        // 验证成功！将密钥与用户绑定并启用MFA
        userService.enableMfa(userLoginHolder.getUserId(), request.secret());

        return ResultUtil.success();
    }

    @PostMapping("/mfa/disable")
    @Permission("sys:user:update")
    @Operation(summary = "验证并禁用两步验证")
    public ResponseResult<Object> verifyDisableMfa(@RequestBody MfaVerifyRequest request) {
        if (!userService.isMfaEnabled(userLoginHolder.getUsername())) {
            throw new CommonException(ExceptionType.EXISTING_RESOURCES.getCode(), "MFA未启用");
        }

        // 对用户输入的code进行验证
        if (totpService.isCodeInvalid(request.code(), userLoginHolder.getUsername())) {
            return ResultUtil.error(messageUtil.getMessage("login.mfaError"));
        }

        // 验证成功！禁用MFA
        userService.disableMfa(userLoginHolder.getUserId());

        return ResultUtil.success();
    }
}
