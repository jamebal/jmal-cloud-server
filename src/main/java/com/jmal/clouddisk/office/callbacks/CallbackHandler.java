package com.jmal.clouddisk.office.callbacks;

import com.jmal.clouddisk.office.model.Track;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jmal
 * @Description CallbackHandler
 * @date 2022/8/11 16:29
 */
@Service
@Slf4j
public class CallbackHandler {

    private final Map<Integer, Callback> callbackHandlers = new HashMap<>();

    public void register(int code, Callback callback){
        // 注册一个回调处理程序
        callbackHandlers.put(code, callback);
    }

    public int handle(Track body){
        Callback callback = callbackHandlers.get(body.getStatus());
        if (callback == null){
            log.warn("Callback status "+body.getStatus()+" is not supported yet");
           return 0;
        }
        return callback.handle(body);
    }
}
