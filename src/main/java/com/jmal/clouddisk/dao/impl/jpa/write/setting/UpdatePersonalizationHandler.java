package com.jmal.clouddisk.dao.impl.jpa.write.setting;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.WebsiteSettingRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("settingUpdatePersonalizationHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdatePersonalizationHandler implements IDataOperationHandler<WebSiteSettingOperation.UpdatePersonalization, Integer> {

    private final WebsiteSettingRepository repo;

    @Override
    public Integer handle(WebSiteSettingOperation.UpdatePersonalization op) {
        return repo.updatePersonalization(op.personalization());
    }
}
