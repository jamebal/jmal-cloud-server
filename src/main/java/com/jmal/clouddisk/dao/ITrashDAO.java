package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.Trash;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;

import java.util.List;

public interface ITrashDAO {
    long getOccupiedSpace(String userId, String collectionName);

    void saveAll(List<Trash> trashList);

    FileDocument findAndRemoveById(String trashFileId);

    List<String> findAllIdsAndRemove(String userId);

    List<FileBaseDTO> findAllFileBaseDTOAndRemoveByIdIn(List<String> fileIds);
}
