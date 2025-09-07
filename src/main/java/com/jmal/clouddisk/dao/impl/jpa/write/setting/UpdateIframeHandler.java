package com.jmal.clouddisk.dao.impl.jpa.write.setting;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.WebsiteSettingRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("settingUpdateIframeHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateIframeHandler implements IDataOperationHandler<WebSiteSettingOperation.UpdateIframe, Integer> {

    private final WebsiteSettingRepository repo;

    @Override
    public Integer handle(WebSiteSettingOperation.UpdateIframe op) {
        return repo.updateIframe(op.iframe());
    }
}
