package com.jmal.clouddisk.oss;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("oss/aliyun")
@Tag(name = "阿里云oss")
public class AliyunController {

    private final AliyunService aliyunService;

    public AliyunController(AliyunService aliyunService) {
        this.aliyunService = aliyunService;
    }

    @Operation(summary = "获取appToken")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    @GetMapping("getAppToken")
    public ResponseResult<Object> login() throws Exception {
        return aliyunService.getAppToken();
    }
}
