package com.jmal.clouddisk.dao.impl.jpa.write.trash;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.TrashRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.file.TrashEntityDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("trashCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<TrashOperation.CreateAll, Void> {

    private final TrashRepository repo;

    private final FilePersistenceService filePersistenceService;

    @Override
    public Void handle(TrashOperation.CreateAll op) {
        List<TrashEntityDO> trashEntityDOList = op.trashes().stream().map(trash -> {
            TrashEntityDO trashEntityDO = new TrashEntityDO(trash);
            filePersistenceService.persistContents(trash);
            return trashEntityDO;
        }).toList();
        repo.saveAll(trashEntityDOList);
        return null;
    }
}
