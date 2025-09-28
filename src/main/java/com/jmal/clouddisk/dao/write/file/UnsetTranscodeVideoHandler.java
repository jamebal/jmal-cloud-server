package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.FilePropsRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileUnsetTranscodeVideoHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UnsetTranscodeVideoHandler implements IDataOperationHandler<FileOperation.UnsetTranscodeVideo, Void> {

    private final FilePropsRepository repo;

    @Override
    public Void handle(FileOperation.UnsetTranscodeVideo op) {
        repo.unsetTranscodeVideo();
        return null;
    }
}
