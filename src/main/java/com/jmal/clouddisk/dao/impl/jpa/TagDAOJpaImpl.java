package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.ITagDAO;
import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TagRepository;
import com.jmal.clouddisk.model.TagDO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class TagDAOJpaImpl implements ITagDAO {

    private final TagRepository tagRepository;

    @Override
    @Transactional
    public TagDO findById(String tagId) {
        return tagRepository.findById(tagId).orElse(null);
    }

    @Override
    public List<TagDO> findAllById(List<String> tagIds) {
        return tagRepository.findAllById(tagIds);
    }

    @Override
    @Transactional
    public List<TagDO> findTags(String userId) {
        String queryUserId = CharSequenceUtil.isBlank(userId) ? null : userId;
        return tagRepository.findTagsByUserIdOrNull(queryUserId);
    }

    @Override
    @Transactional
    public TagDO findOneTagByUserIdAndName(String userId, String tagName) {
        String queryUserId = CharSequenceUtil.isBlank(userId) ? null : userId;
        return tagRepository.findOneTagByUserIdAndName(queryUserId, tagName).orElse(null);
    }

    @Override
    @Transactional
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
    @Transactional
    public boolean existsByNameAndIdNot(String name, String id) {
        return tagRepository.existsByNameAndIdNot(name, id);
    }

    @Override
    @Transactional
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
