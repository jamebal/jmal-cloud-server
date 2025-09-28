package com.jmal.clouddisk.dao.write.menu;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.MenuRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("menuCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<MenuOperation.CreateAll, Void> {

    private final MenuRepository repo;

    @Override
    public Void handle(MenuOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}
