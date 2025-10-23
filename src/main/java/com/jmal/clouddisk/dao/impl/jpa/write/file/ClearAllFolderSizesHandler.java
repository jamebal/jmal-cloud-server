package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FolderSizeRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileClearAllFolderSizesHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ClearAllFolderSizesHandler implements IDataOperationHandler<FileOperation.ClearAllFolderSizes, Integer> {

    private final FolderSizeRepository repo;

    @Override
    public Integer handle(FileOperation.ClearAllFolderSizes op) {
        return repo.clearAllFolderSizes();
    }
}
