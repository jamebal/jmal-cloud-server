package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUpdateTranscodeVideoByIdInHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateTranscodeVideoByIdInHandler implements IDataOperationHandler<FileOperation.UpdateTranscodeVideoByIdIn, Integer> {

    private final FilePropsRepository repo;

    @Override
    public Integer handle(FileOperation.UpdateTranscodeVideoByIdIn op) {
        return repo.updateTranscodeVideoByIdIn(op.fileIdList(), op.status());
    }
}
