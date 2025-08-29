package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.TagDO;

import java.util.List;

public interface ITagDAO {

    List<TagDO> findTags(String userId);

    TagDO findOneTagByUserIdAndName(String userId, String tagName);

    TagDO findOneTagByUserIdAndSlugName(String userId, String tagSlugName);
}
