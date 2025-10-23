package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUnsetSharePropsHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UnsetSharePropsHandler implements IDataOperationHandler<FileOperation.UnsetShareProps, Integer> {

    private final FilePropsRepository repo;

    @Override
    public Integer handle(FileOperation.UnsetShareProps op) {
        if (op.isFolder()) {
            return repo.unsetFolderShareProps(op.userId(), op.pathPrefixForLike(), op.shareProperties());
        } else {
            return repo.unsetFileShareProps(op.fileId(), op.shareProperties());
        }
    }
}
