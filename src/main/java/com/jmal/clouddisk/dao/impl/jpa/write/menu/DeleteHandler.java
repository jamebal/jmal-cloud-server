package com.jmal.clouddisk.dao.impl.jpa.write.menu;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.MenuRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("menuDeleteHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteHandler implements IDataOperationHandler<MenuOperation.Delete, Void> {

    private final MenuRepository repo;

    @Override
    public Void handle(MenuOperation.Delete op) {
        repo.delete(op.entity());
        return null;
    }
}
