package com.jmal.clouddisk.dao.impl.jpa.write.setting;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.WebsiteSettingRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("settingCreateAllOperationHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllOperationHandler implements IDataOperationHandler<WebSiteSettingOperation.CreateAll, Void> {

    private final WebsiteSettingRepository repo;

    @Override
    public Void handle(WebSiteSettingOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
