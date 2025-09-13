package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.ShareProperties;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.model.file.dto.FileBaseOssPathDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface IFileDAO {

    void deleteAllByIdInBatch(List<String> userIdList);

    void updateIsPublicById(String fileId);

    void updateTagInfoInFiles(String tagId, String newTagName, String newColor);

    /**
     * 获取文件夹下的子分享文件ID列表
     * @param userId userId
     * @param pathPrefix pathPrefix
     * @return List<String>
     */
    List<String> findIdSubShare(String userId, String pathPrefix);


    /**
     * 判断文件夹下是否存在子分享
     * @param userId userId
     * @param pathPrefix pathPrefix
     * @return boolean
     */
    boolean existsFolderSubShare(String userId, String pathPrefix);

    boolean existsById(String id);

    void save(FileDocument file);

    String upsertMountFile(FileDocument fileDocument);

    boolean existsByUserIdAndMountFileId(String userId, String fileId);

    String findMountFilePath(String fileId, String userId);

    /**
     * 通过文件ID列表查询存在的文件ID列表
     * 改方法是为了验证文件ID列表中哪些是存在的
     *
     * @param fileIdList fileIdList
     * @return List<String>
     */
    List<String> findByIdIn(List<String> fileIdList);

    List<FileDocument> findAllAndRemoveByUserIdAndIdPrefix(String userId, String idPrefix);

    void saveAll(List<FileDocument> fileDocumentList);

    void removeByMountFileId(String fileId);

    void setSubShareByFileId(String fileId);

    void unsetSubShareByFileId(String fileId);

    boolean existsByNameAndIdNotIn(String filename, String fileId);

    boolean existsBySlugAndIdNot(String slug, String fileId);

    boolean existsByUserIdAndPathAndNameIn(String path, String userId, List<String> filenames);

    boolean existsByUserIdAndPathAndMd5(String userId, String path, String md5);

    void updateShareProps(FileDocument fileDocument, String shareId, ShareProperties shareProperties, boolean isFolder);

    void updateShareFirst(String fileId, boolean shareBase);

    void unsetShareProps(FileDocument file, boolean isFolder);

    void setSubShareFormShareBase(FileDocument file);

    FileDocument findByUserIdAndPathAndName(String userId, String path, String name, String... excludeFields);

    FileBaseDTO findFileBaseDTOByUserIdAndPathAndName(String userId, String path, String name);

    String findIdByUserIdAndPathAndName(String userId, String path, String name);

    long updateModifyFile(String id, long length, String md5, String suffix, String fileContentType, LocalDateTime updateTime);

    List<FileBaseDTO> findAllFileBaseDTOAndRemoveByIdIn(List<String> fileIds);

    void removeAllByFolder(FileBaseDTO FileBaseDTO);

    List<String> findAllIdsAndRemoveByFolder(FileBaseDTO fileBaseDTO);

    List<FileDocument> findAllAndRemoveByFolder(FileBaseDTO fileBaseDTO);

    FileBaseOssPathDTO findFileBaseOssPathDTOById(String id);

    List<FileBaseOssPathDTO> findFileBaseOssPathDTOByIdIn(List<String> fileIds);

    void removeByUserIdAndPathAndName(String userId, String path, String name);

    long countByDelTag(int delTag);

    List<FileBaseDTO> findFileBaseDTOByDelTagOfLimit(int delTag, int limit);

    void removeById(String fileId);

    long unsetDelTag(String fileId);

    void removeByIdIn(List<String> fileIds);

    List<FileDocument> findAllAndRemoveByIdIn(List<String> fileIds);

    FileBaseDTO findFileBaseDTOById(String fileId);

    void setIsFavoriteByIdIn(List<String> fileIds, boolean isFavorite);

    void setNameAndSuffixById(String name, String suffix, String fileId);
}
