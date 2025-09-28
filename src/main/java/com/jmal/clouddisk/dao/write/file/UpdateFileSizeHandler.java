package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FolderSizeRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateFileSizeHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateFileSizeHandler implements IDataOperationHandler<FileOperation.UpdateFileSize, Integer> {

    private final FolderSizeRepository repo;

    @Override
    public Integer handle(FileOperation.UpdateFileSize op) {
        return repo.updateFileSize(op.fileId(), op.size());
    }
}
