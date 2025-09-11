package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.ShareProperties;

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
}
