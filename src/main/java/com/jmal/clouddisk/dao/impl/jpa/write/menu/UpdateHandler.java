package com.jmal.clouddisk.dao.impl.jpa.write.menu;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.MenuRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("menuUpdateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateHandler implements IDataOperationHandler<MenuOperation.Update, Void> {

    private final MenuRepository repo;

    @Override
    public Void handle(MenuOperation.Update op) {
        repo.save(op.entity());
        return null;
    }
}
