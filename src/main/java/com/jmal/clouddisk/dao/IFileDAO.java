package com.jmal.clouddisk.dao;

import java.util.List;

public interface IFileDAO {

    void deleteAllByIdInBatch(List<String> userIdList);

    void updateIsPublicById(String fileId);

    void updateTagInfoInFiles(String tagId, String newTagName, String newColor);
}
