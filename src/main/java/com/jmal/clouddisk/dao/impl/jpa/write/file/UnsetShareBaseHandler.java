package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUnsetShareBaseHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UnsetShareBaseHandler implements IDataOperationHandler<FileOperation.UnsetShareBaseOperation, Void> {

    private final FilePropsRepository repo;

    @Override
    public Void handle(FileOperation.UnsetShareBaseOperation op) {
        repo.unsetSubShareByFileId(op.fileId());
        return null;
    }
}
