package com.jmal.clouddisk.dao.impl.jpa.write.setting;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.HeartwingsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("settingCreateHeartwingsHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHeartwingsHandler implements IDataOperationHandler<WebSiteSettingOperation.CreateHeartwings, Void> {

    private final HeartwingsRepository repo;

    @Override
    public Void handle(WebSiteSettingOperation.CreateHeartwings op) {
        repo.save(op.entity());
        return null;
    }
}
