package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FilePropsRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateSharePropsHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateSharePropsHandler implements IDataOperationHandler<FileOperation.UpdateShareProps, Integer> {

    private final FilePropsRepository repo;

    @Override
    public Integer handle(FileOperation.UpdateShareProps op) {
        if (op.isFolder()) {
            return repo.updateFolderShareProps(op.userId(), op.pathPrefix(), op.shareId(), op.shareProps());
        } else {
            return repo.updateFileShareProps(op.fileId(), op.shareId(), op.shareProps());
        }
    }
}
