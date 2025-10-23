package com.jmal.clouddisk.dao.impl.jpa.write.file;

import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileSetSetOtherPropsByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SetSetOtherPropsByIdHandler implements IDataOperationHandler<FileOperation.SetOtherPropsById, Void> {

    private final FilePropsRepository repo;
    private final FileMetadataRepository fileMetadataRepository;

    @Override
    public Void handle(FileOperation.SetOtherPropsById op) {
        repo.setPropsById(op.id(), op.otherProperties());
        if (op.otherProperties() != null && BooleanUtil.isTrue(op.otherProperties().getShowCover())) {
            fileMetadataRepository.setContentById(op.id());
        }
        return null;
    }
}
