package com.jmal.clouddisk.office.callbacks;

import com.jmal.clouddisk.office.model.Track;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author jmal
 * @Description Callback
 * @date 2022/8/11 16:29
 */
public interface Callback {
    /**
     * 处理回调
     * @param body body
     * @param fileName fileName
     * @return 返回码
     */
    int handle(Track body, String fileName);

    /**
     * 获取文档状态
     * @return Status
     */
    int getStatus();

    /**
     * 注册一个回调处理程序
     * @param callbackHandler CallbackHandler
     */
    @Autowired
    default void selfRegistration(CallbackHandler callbackHandler){
        callbackHandler.register(getStatus(), this);
    }
}
