package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateTagsForFileHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateTagsForFileHandler implements IDataOperationHandler<FileOperation.UpdateTagsForFile, Void> {

    private final FilePropsRepository repo;

    @Override
    public Void handle(FileOperation.UpdateTagsForFile op) {
        repo.updateTagsForFile(op.fileId(), op.tags());
        return null;
    }
}
