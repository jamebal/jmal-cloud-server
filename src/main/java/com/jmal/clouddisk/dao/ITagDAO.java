package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.TagDO;

import java.util.List;

public interface ITagDAO {

    TagDO findById(String tagId);

    List<TagDO> findAllById(List<String> tagIds);

    List<TagDO> findTags(String userId);

    TagDO findOneTagByUserIdAndName(String userId, String tagName);

    TagDO findOneTagByUserIdAndSlugName(String userId, String tagSlugName);

    void save(TagDO tag);

    boolean existsByNameAndIdNot(String name, String id);

    boolean existsBySlugAndIdNot(String slug, String id);

    void removeByIdIn(List<String> tagIdList);

    void updateSortById(String tagId, Integer sort);
}
