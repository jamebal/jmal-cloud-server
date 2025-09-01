package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.ITagDAO;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TagRepository;
import com.jmal.clouddisk.model.TagDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class TagDAOJpaImpl implements ITagDAO {

    private final TagRepository tagRepository;

    @Override
    @Transactional(readOnly = true)
    public TagDO findById(String tagId) {
        return tagRepository.findById(tagId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagDO> findAllById(List<String> tagIds) {
        return tagRepository.findAllById(tagIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagDO> findTags(String userId) {
        String queryUserId = CharSequenceUtil.isBlank(userId) ? null : userId;
        return tagRepository.findTagsByUserIdOrNull(queryUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public TagDO findOneTagByUserIdAndName(String userId, String tagName) {
        String queryUserId = CharSequenceUtil.isBlank(userId) ? null : userId;
        return tagRepository.findOneTagByUserIdAndName(queryUserId, tagName).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public TagDO findOneTagByUserIdAndSlugName(String userId, String tagSlugName) {
        String queryUserId = CharSequenceUtil.isBlank(userId) ? null : userId;
        return tagRepository.findOneTagByUserIdAndSlug(queryUserId, tagSlugName).orElse(null);
    }

    @Override
    @Transactional
    public void save(TagDO tag) {
        tagRepository.save(tag);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByNameAndIdNot(String name, String id) {
        return tagRepository.existsByNameAndIdNot(name, id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlugAndIdNot(String slug, String id) {
        return tagRepository.existsBySlugAndIdNot(slug, id);
    }

    @Override
    @Transactional
    public void removeByIdIn(List<String> tagIdList) {
        tagRepository.removeByIdIn(tagIdList);
    }

    @Override
    @Transactional
    public void updateSortById(String tagId, Integer sort) {
        tagRepository.updateSortById(tagId, sort);
    }

}
