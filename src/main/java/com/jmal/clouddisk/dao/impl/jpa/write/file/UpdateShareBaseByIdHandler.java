package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateShareBaseByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateShareBaseByIdHandler implements IDataOperationHandler<FileOperation.UpdateShareBaseById, Integer> {

    private final FilePropsRepository repo;

    @Override
    public Integer handle(FileOperation.UpdateShareBaseById op) {
        return repo.updateShareBaseById(op.shareBase(), op.shareId(), op.shareProps(), op.fileId());
    }
}
