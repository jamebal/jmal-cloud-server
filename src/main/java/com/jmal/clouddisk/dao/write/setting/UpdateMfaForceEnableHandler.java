package com.jmal.clouddisk.dao.write.setting;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.WebsiteSettingRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("settingUpdateMfaForceEnableHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateMfaForceEnableHandler implements IDataOperationHandler<WebSiteSettingOperation.UpdateMfaForceEnable, Integer> {

    private final WebsiteSettingRepository repo;

    @Override
    public Integer handle(WebSiteSettingOperation.UpdateMfaForceEnable op) {
        return repo.updateMfaForceEnable(op.mfaForceEnable());
    }
}
