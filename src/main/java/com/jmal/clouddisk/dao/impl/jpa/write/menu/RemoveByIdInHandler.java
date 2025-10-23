package com.jmal.clouddisk.dao.impl.jpa.write.menu;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.MenuRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("menuRemoveByIdInHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByIdInHandler implements IDataOperationHandler<MenuOperation.RemoveByIdIn, Void> {

    private final MenuRepository repo;

    @Override
    public Void handle(MenuOperation.RemoveByIdIn op) {
        repo.removeByIdIn(op.idList());
        return null;
    }
}
