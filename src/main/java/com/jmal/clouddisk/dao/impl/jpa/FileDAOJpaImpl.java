package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FilePropsRepository;
import com.jmal.clouddisk.lucene.LuceneQueryService;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FilePropsDO;
import com.jmal.clouddisk.model.file.ShareProperties;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class FileDAOJpaImpl implements IFileDAO {

    private final FileMetadataRepository fileMetadataRepository;

    private final FilePropsRepository filePropsRepository;

    private final LuceneQueryService luceneQueryService;

    @Override
    public void deleteAllByIdInBatch(List<String> userIdList) {
        fileMetadataRepository.deleteAllByIdInBatch(userIdList);
    }

    @Override
    @Transactional
    public void updateIsPublicById(String fileId) {
        FilePropsDO props = filePropsRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("FilePropsDO not found with id: " + fileId));

        if (props.getShareProps() == null) {
            props.setShareProps(new ShareProperties());
        }
        props.getShareProps().setIsPublic(true);
    }

    @Override
    @Transactional
    public void updateTagInfoInFiles(String tagId, String newTagName, String newColor) {
        Set<String> affectedFileIds = luceneQueryService.findByTagId(tagId);
        if (affectedFileIds == null || affectedFileIds.isEmpty()) {
            return;
        }
        List<FilePropsDO> propsToUpdate = filePropsRepository.findAllById(affectedFileIds);
        for (FilePropsDO props : propsToUpdate) {
            if (props.getTags() == null) continue;
            for (Tag tag : props.getTags()) {
                if (tagId.equals(tag.getTagId())) {
                    // 更新 name 和 color
                    tag.setName(newTagName);
                    tag.setColor(newColor);
                    break;
                }
            }
        }
        filePropsRepository.saveAll(propsToUpdate);
    }
}
