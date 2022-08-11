package com.jmal.clouddisk.office.callbacks.implementations;

import com.jmal.clouddisk.office.callbacks.Callback;
import com.jmal.clouddisk.office.callbacks.Status;
import com.jmal.clouddisk.office.model.Action;
import com.jmal.clouddisk.office.model.Track;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author jmal
 * @Description 在编辑文档时处理回调
 * @date 2022/8/11 16:29
 */
@Component
public class EditCallback implements Callback {

    @Override
    public int handle(Track body, String fileName) {
        return 0;
    }

    @Override
    public int getStatus() {
        // 1 -文件正在编辑中
        return Status.EDITING.getCode();
    }
}
