package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FilePropsRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateTagsForFilesHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateTagsForFilesHandler implements IDataOperationHandler<FileOperation.UpdateTagsForFiles, Void> {

    private final FilePropsRepository repo;

    @Override
    public Void handle(FileOperation.UpdateTagsForFiles op) {
        repo.updateTagsForFiles(op.fileIds(), op.tags());
        return null;
    }
}
