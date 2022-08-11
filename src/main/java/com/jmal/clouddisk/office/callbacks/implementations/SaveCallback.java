package com.jmal.clouddisk.office.callbacks.implementations;
import com.jmal.clouddisk.office.callbacks.Callback;
import com.jmal.clouddisk.office.callbacks.Status;
import com.jmal.clouddisk.office.model.Track;
import org.springframework.stereotype.Component;

/**
 * @author jmal
 * @Description 在执行保存请求时处理回调
 * @date 2022/8/11 16:29
 */
@Component
public class SaveCallback implements Callback {

    @Override
    public int handle(Track body, String fileName) {
        return 0;
    }

    @Override
    public int getStatus() {
        // 文件已准备好保存
        return Status.SAVE.getCode();
    }
}
