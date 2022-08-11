package com.jmal.clouddisk.office;

import com.alibaba.fastjson.JSON;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.office.callbacks.CallbackHandler;
import com.jmal.clouddisk.office.model.Track;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author jmal
 * @Description office文件管理
 * @date 2022/8/11 16:29
 */
@Tag(name = "office文件管理")
@Slf4j
@RestController
public class OfficeController {

    @Autowired
    CallbackHandler callbackHandler;

    @Operation(summary = "office 回调")
    @PostMapping("/office/track")
    @Permission("cloud:file:list")
    public String track(@RequestParam("fileName") String fileName, @RequestParam("userAddress") String userAddress, @RequestBody Track body){
        try {
            log.info("callbackHandler, {}", JSON.toJSONString(body));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return e.getMessage();
        }
        int error = callbackHandler.handle(body, fileName);
        return"{\"error\":" + error + "}";
    }

}
