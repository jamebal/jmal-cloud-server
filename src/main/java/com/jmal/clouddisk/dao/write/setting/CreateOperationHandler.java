package com.jmal.clouddisk.dao.write.setting;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.WebsiteSettingRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("settingCreateAOperationHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateOperationHandler implements IDataOperationHandler<WebSiteSettingOperation.Create, Void> {

    private final WebsiteSettingRepository repo;

    @Override
    public Void handle(WebSiteSettingOperation.Create op) {
        repo.save(op.entity());
        return null;
    }
}
