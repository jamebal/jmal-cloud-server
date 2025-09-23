package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.ITagDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.TagRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.tag.TagOperation;
import com.jmal.clouddisk.model.TagDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class TagDAOJpaImpl implements ITagDAO, IWriteCommon<TagDO> {

    private final TagRepository tagRepository;

    private final IWriteService writeService;

    @Override
    public TagDO findById(String tagId) {
        return tagRepository.findById(tagId).orElse(null);
    }

    @Override
    public List<TagDO> findAllById(List<String> tagIds) {
        return tagRepository.findAllById(tagIds);
    }

    @Override
    public List<TagDO> findTags(String userId) {
        String queryUserId = CharSequenceUtil.isBlank(userId) ? null : userId;
        return tagRepository.findTagsByUserIdOrNull(queryUserId);
    }

    @Override
    public TagDO findOneTagByUserIdAndName(String userId, String tagName) {
        String queryUserId = CharSequenceUtil.isBlank(userId) ? null : userId;
        return tagRepository.findOneTagByUserIdAndName(queryUserId, tagName).orElse(null);
    }

    @Override
    public TagDO findOneTagByUserIdIsNullAndSlugName(String tagSlugName) {
        return tagRepository.findOneTagByUserIdIsNullAndSlug(tagSlugName).orElse(null);
    }

    @Override
    public void save(TagDO tag) {
        writeService.submit(new TagOperation.Create(tag));
    }

    @Override
    public boolean existsByNameAndIdNot(String name, String id) {
        return tagRepository.existsByNameAndIdNotAndUserIdIsNull(name, id);
    }

    @Override
    public boolean existsBySlugAndIdNot(String slug, String id) {
        return tagRepository.existsBySlugAndIdNotAndUserIdIsNull(slug, id);
    }

    @Override
    public void removeByIdIn(List<String> tagIdList) {
        writeService.submit(new TagOperation.RemoveByIdIn(tagIdList));
    }

    @Override
    public void updateSortById(String tagId, Integer sort) {
        writeService.submit(new TagOperation.UpdateSortById(tagId, sort));
    }

    @Override
    public void AsyncSaveAll(Iterable<TagDO> entities) {
        writeService.submit(new TagOperation.CreateAll(entities));
    }
}
