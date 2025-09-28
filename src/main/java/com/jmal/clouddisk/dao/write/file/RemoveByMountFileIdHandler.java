package com.jmal.clouddisk.dao.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.ArticleRepository;
import com.jmal.clouddisk.dao.repository.jpa.FileMetadataRepository;
import com.jmal.clouddisk.dao.repository.jpa.FilePropsRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("fileRemoveByMountFileIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByMountFileIdHandler implements IDataOperationHandler<FileOperation.RemoveByMountFileIdIn, Void> {

    private final FileMetadataRepository repo;
    private final FilePropsRepository filePropsRepository;
    private final ArticleRepository articleRepository;

    @Override
    public Void handle(FileOperation.RemoveByMountFileIdIn op) {
        List<String> ids = repo.findAllPublicIdsByMountFileId(op.fileIds());
        repo.removeByMountFileIdIn(op.fileIds());
        filePropsRepository.deleteAllByPublicIdIn(ids);
        articleRepository.deleteAllByPublicIdIn(ids);
        return null;
    }
}
