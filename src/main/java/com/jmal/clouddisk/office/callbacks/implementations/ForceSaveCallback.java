package com.jmal.clouddisk.office.callbacks.implementations;

import com.jmal.clouddisk.office.callbacks.Callback;
import com.jmal.clouddisk.office.callbacks.Status;
import com.jmal.clouddisk.office.model.Track;
import org.springframework.stereotype.Component;

/**
 * @author jmal
 * @Description 执行强制保存请求时处理回调
 * @date 2022/8/11 16:29
 */
@Component
public class ForceSaveCallback implements Callback {

    @Override
    public int handle(Track body, String fileName) {
        return 0;
    }

    @Override
    public int getStatus() {
        // 6 -正在编辑文档，但保存了当前文档状态
        return Status.MUST_FORCE_SAVE.getCode();
    }
}
