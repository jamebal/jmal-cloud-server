package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FilePropsRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetShareBaseHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetShareBaseHandler implements IDataOperationHandler<FileOperation.SetShareBaseOperation, Void> {

    private final FilePropsRepository repo;

    @Override
    public Void handle(FileOperation.SetShareBaseOperation op) {
        repo.setSubShareByFileId(op.fileId());
        return null;
    }
}
