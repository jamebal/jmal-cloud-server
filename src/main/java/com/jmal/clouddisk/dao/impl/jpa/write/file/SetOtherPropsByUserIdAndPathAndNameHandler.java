package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetOtherPropsByUserIdAndPathAndNameHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetOtherPropsByUserIdAndPathAndNameHandler implements IDataOperationHandler<FileOperation.setOtherPropsByUserIdAndPathAndName, Void> {

    private final FilePropsRepository repo;

    @Override
    public Void handle(FileOperation.setOtherPropsByUserIdAndPathAndName operation) {
        repo.setOtherPropsByUserIdAndPathAndName(operation.otherProperties(), operation.userId(), operation.path(), operation.name());
        return null;
    }
}
