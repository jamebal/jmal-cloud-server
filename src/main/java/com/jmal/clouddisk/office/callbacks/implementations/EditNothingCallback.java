package com.jmal.clouddisk.office.callbacks.implementations;

import com.jmal.clouddisk.office.callbacks.Callback;
import com.jmal.clouddisk.office.callbacks.Status;
import com.jmal.clouddisk.office.model.Track;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author jmal
 * @Description 在编辑文档时处理回调
 * @date 2022/8/11 16:29
 */
@Component
@Slf4j
public class EditNothingCallback implements Callback {

    @Override
    public int handle(Track body) {
        return 0;
    }

    @Override
    public int getStatus() {
        // 4 - 退出正在编辑的文件
        return Status.EDIT_NOTHING.getCode();
    }
}
