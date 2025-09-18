package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.file.dto.FileBaseEtagDTO;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface IEtagDAO {

    long countFoldersWithoutEtag();

    void setFoldersWithoutEtag();

    long getFolderSize(String userId, String path);

    boolean existsByNeedsEtagUpdateFolder();

    String findEtagByUserIdAndPathAndName(String userId, String path, String name);

    void setEtagByUserIdAndPathAndName(String userId, String path, String name, String newEtag);

    boolean existsByUserIdAndPath(String userId, String path);

    long countRootDirFilesWithoutEtag();

    List<FileBaseEtagDTO> findFileBaseEtagDTOByRootDirFilesWithoutEtag();

    List<FileBaseEtagDTO> findFileBaseEtagDTOByNeedUpdateFolder(Sort sort);

    void clearMarkUpdateById(String fileId);

    boolean setMarkUpdateByUserIdAndPathAndName(String userId, String path, String name);

    List<FileBaseEtagDTO> findFileBaseEtagDTOByUserIdAndPath(String userId, String path);

    long updateEtagAndSizeById(String fileId, String etag, long size);

    int findEtagUpdateFailedAttemptsById(String fileId);

    void setFailedEtagById(String fileId, int attempts, String errorMsg, Boolean needsEtagUpdate);
}
