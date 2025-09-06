package com.jmal.clouddisk.dao.impl.jpa.write.setting;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.WebsiteSettingRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class WebSiteSettingOperationHandler implements IDataOperationHandler<IWebSiteSettingOperation> {

    private final WebsiteSettingRepository websiteSettingRepository;

    @Override
    public void handle(IWebSiteSettingOperation operation) {
        switch (operation) {
            case WebSiteSettingOperation.CreateAll createOp -> websiteSettingRepository.saveAll(createOp.entities());
        }
    }

}
