package com.jmal.clouddisk.dao.write.setting;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.WebsiteSettingRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("settingUpdateNetdiskLogoHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateNetdiskLogoHandler implements IDataOperationHandler<WebSiteSettingOperation.UpdateNetdiskLogo, Integer> {

    private final WebsiteSettingRepository repo;

    @Override
    public Integer handle(WebSiteSettingOperation.UpdateNetdiskLogo op) {
        return repo.updateNetdiskLogo(op.logo());
    }
}
