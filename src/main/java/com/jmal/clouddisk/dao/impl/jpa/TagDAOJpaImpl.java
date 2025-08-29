package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.ITagDAO;
import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TagRepository;
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
public class TagDAOJpaImpl implements ITagDAO {

    private final TagRepository tagRepository;

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
    public TagDO findOneTagByUserIdAndSlugName(String userId, String tagSlugName) {
        String queryUserId = CharSequenceUtil.isBlank(userId) ? null : userId;
        return tagRepository.findOneTagByUserIdAndSlug(queryUserId, tagSlugName).orElse(null);
    }

}
