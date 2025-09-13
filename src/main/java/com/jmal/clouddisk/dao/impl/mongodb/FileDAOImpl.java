package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.ShareProperties;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.model.file.dto.FileBaseOssPathDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.MongoUtil;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.jmal.clouddisk.service.IUserService.USER_ID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class FileDAOImpl implements IFileDAO {

    private final MongoTemplate mongoTemplate;

    private final UserLoginHolder userLoginHolder;

    @Override
    public void deleteAllByIdInBatch(List<String> userIdList) {
        Query query = new Query();
        query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where(IUserService.USER_ID).in(userIdList));
        mongoTemplate.remove(query, FileDocument.class);
    }

    @Override
    public void updateIsPublicById(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set("isPublic", true);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public void updateTagInfoInFiles(String tagId, String newTagName, String newColor) {
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("tags.tagId").is(tagId));
        Update update = new Update();
        update.set("tags.$.color", newColor);
        update.set("tags.$.name", newTagName);
        mongoTemplate.updateMulti(query1,update, FileDocument.class);
    }

    /**
     * 获取文件夹下的子分享的查询条件
     * @return Query
     */
    private static Query getFolderSubShareQuery(String userId, String pathPrefix) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").regex("^" + pathPrefix));
        query.addCriteria(Criteria.where(Constants.SHARE_BASE).is(true));
        return query;
    }

    @Override
    public List<String> findIdSubShare(String userId, String pathPrefix) {
        Query query = getFolderSubShareQuery(userId, pathPrefix);
        query.fields().include("_id");
        List<FileDocument> list = mongoTemplate.find(getFolderSubShareQuery(userId, pathPrefix), FileDocument.class);
        return list.stream().map(FileDocument::getId).toList();
    }

    @Override
    public boolean existsFolderSubShare(String userId, String pathPrefix) {
        Query query = getFolderSubShareQuery(userId, pathPrefix);
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public boolean existsById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public void save(FileDocument file) {
        mongoTemplate.save(file);
    }

    @Override
    public String upsertMountFile(FileDocument fileDocument) {
        Update update = MongoUtil.getUpdate(fileDocument);
        update.set("remark", "挂载 mount");
        Query query = CommonFileService.getQuery(fileDocument);
        UpdateResult updateResult = mongoTemplate.upsert(query, update, FileDocument.class);
        if (updateResult.getUpsertedId() != null) {
            return updateResult.getUpsertedId().asString().getValue();
        }
        return null;
    }

    private static Query getMountQuery(String fileId, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("mountFileId").is(fileId));
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        return query;
    }

    @Override
    public boolean existsByUserIdAndMountFileId(String userId, String fileId) {
        Query query = getMountQuery(fileId, userId);
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public String findMountFilePath(String fileId, String userId) {
        Query query = getMountQuery(fileId, userId);
        query.fields().include("path");
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        return fileDocument != null ? fileDocument.getPath() : null;
    }

    @Override
    public List<String> findByIdIn(List<String> fileIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList));
        query.fields().include("_id");
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class);
        return list.stream().map(FileDocument::getId).toList();
    }

    @Override
    public List<FileDocument> findAllAndRemoveByUserIdAndIdPrefix(String userId, String idPrefix) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("_id").regex("^" + idPrefix));
        return mongoTemplate.findAllAndRemove(query, FileDocument.class);
    }

    @Override
    public void saveAll(List<FileDocument> fileDocumentList) {
        mongoTemplate.insertAll(fileDocumentList);
    }

    @Override
    public void removeByMountFileId(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("mountFileId").is(fileId));
        mongoTemplate.remove(query, FileDocument.class);
    }

    @Override
    public void setSubShareByFileId(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set(Constants.SUB_SHARE, true);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public void unsetSubShareByFileId(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.unset(Constants.SUB_SHARE);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public boolean existsByNameAndIdNotIn(String filename, String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(filename));
        query.addCriteria(Criteria.where("_id").ne(fileId));
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public boolean existsBySlugAndIdNot(String slug, String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("slug").is(slug));
        if (CharSequenceUtil.isNotBlank(fileId)) {
            query.addCriteria(Criteria.where("_id").ne(fileId));
        }
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public boolean existsByUserIdAndPathAndNameIn(String path, String userId, List<String> filenames) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where(Constants.PATH_FIELD).is(path));
        query.addCriteria(Criteria.where(Constants.FILENAME_FIELD).in(filenames));
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public boolean existsByUserIdAndPathAndMd5(String userId, String path, String md5) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where(Constants.PATH_FIELD).is(path));
        query.addCriteria(Criteria.where("md5").is(md5));
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public void updateShareProps(FileDocument file, String shareId, ShareProperties shareProperties, boolean isFolder) {
        Query query = getUpdateSharePropsQuery(file, isFolder);
        Update update = new Update();
        setShareAttribute(update, shareProperties.getExpiresAt(), shareId, shareProperties.getIsPrivacy(), shareProperties.getExtractionCode(), shareProperties.getOperationPermissionList());
        mongoTemplate.updateMulti(query, update, FileDocument.class);
    }

    @Override
    public void updateShareFirst(String fileId, boolean shareBase) {
        Update update = new Update();
        if (shareBase) {
            update.set(Constants.SHARE_BASE, true);
        } else {
            update.unset(Constants.SHARE_BASE);
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").is(fileId));
        mongoTemplate.updateFirst(query1, update, FileDocument.class);
    }

    @Override
    public void unsetShareProps(FileDocument file, boolean isFolder) {
        Query query = getUpdateSharePropsQuery(file, isFolder);
        Update update = new Update();
        update.unset(Constants.SHARE_ID);
        update.unset(Constants.IS_SHARE);
        update.unset(Constants.SUB_SHARE);
        update.unset(Constants.EXPIRES_AT);
        update.unset(Constants.IS_PRIVACY);
        update.unset(Constants.OPERATION_PERMISSION_LIST);
        update.unset(Constants.EXTRACTION_CODE);
        mongoTemplate.updateMulti(query, update, FileDocument.class);
    }

    @Override
    public void setSubShareFormShareBase(FileDocument file) {
        Query query = getUpdateSharePropsQuery(file, true);
        query.addCriteria(Criteria.where(Constants.SHARE_BASE).is(true));
        Update update = new Update();
        update.unset(Constants.SHARE_BASE);
        update.set(Constants.SUB_SHARE, true);
        mongoTemplate.updateMulti(query, update, FileDocument.class);
    }

    @Override
    public FileDocument findByUserIdAndPathAndName(String userId, String path, String name, String... excludeFields) {
        Query query = CommonFileService.getQuery(userId, path, name);
        query.fields().exclude(excludeFields);
        return mongoTemplate.findOne(query, FileDocument.class);
    }

    @Override
    public FileBaseDTO findFileBaseDTOByUserIdAndPathAndName(String userId, String relativePath, String fileName) {
        Query query = CommonFileService.getQuery(userId, relativePath, fileName);
        return mongoTemplate.findOne(query, FileBaseDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public String findIdByUserIdAndPathAndName(String userId, String path, String name) {
        Query query = CommonFileService.getQuery(userId, path, name);
        query.fields().include("_id");
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        return fileDocument != null ? fileDocument.getId() : null;
    }

    @Override
    public long updateModifyFile(String id, long length, String md5, String suffix, String fileContentType, LocalDateTime updateTime) {
        Update update = new Update();
        update.set(Constants.SIZE, length);
        update.set("md5", md5);
        update.set(Constants.SUFFIX, suffix);
        update.set(Constants.CONTENT_TYPE, fileContentType);
        update.set(Constants.UPDATE_DATE, updateTime);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        UpdateResult updateResult = mongoTemplate.upsert(query, update, FileDocument.class);
        return updateResult.getModifiedCount();
    }

    @Override
    public List<FileBaseDTO> findAllFileBaseDTOAndRemoveByIdIn(List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        return mongoTemplate.findAllAndRemove(query, FileBaseDTO.class, CommonFileService.COLLECTION_NAME);
    }

    private Query getAllByFolderQuery(FileBaseDTO fileBaseDTO) {
        Query query1 = new Query();
        query1.addCriteria(Criteria.where(USER_ID).is(fileBaseDTO.getUserId()));
        query1.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(fileBaseDTO.getPath() + fileBaseDTO.getName() + "/")));
        return query1;
    }

    @Override
    public void removeAllByFolder(FileBaseDTO fileBaseDTO) {
        Query query = getAllByFolderQuery(fileBaseDTO);
        mongoTemplate.remove(query, FileDocument.class);
    }

    @Override
    public List<String> findAllIdsAndRemoveByFolder(FileBaseDTO fileBaseDTO) {
        Query query = getAllByFolderQuery(fileBaseDTO);
        query.fields().include("_id");
        List<FileDocument> list = mongoTemplate.findAllAndRemove(query, FileDocument.class);
        return list.stream().map(FileDocument::getId).toList();
    }

    @Override
    public List<FileDocument> findAllAndRemoveByFolder(FileBaseDTO fileBaseDTO) {
        Query query = getAllByFolderQuery(fileBaseDTO);
        return mongoTemplate.findAllAndRemove(query, FileDocument.class);
    }

    @Override
    public FileBaseOssPathDTO findFileBaseOssPathDTOById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        return mongoTemplate.findOne(query, FileBaseOssPathDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public List<FileBaseOssPathDTO> findFileBaseOssPathDTOByIdIn(List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        return mongoTemplate.find(query, FileBaseOssPathDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public void removeByUserIdAndPathAndName(String userId, String path, String name) {
        Query query = CommonFileService.getQuery(userId, path, name);
        mongoTemplate.remove(query, FileDocument.class);
    }

    @Override
    public long countByDelTag(int delTag) {
        Query query = new Query();
        query.addCriteria(Criteria.where("delete").is(1));
        return mongoTemplate.count(query, FileDocument.class);
    }

    @Override
    public List<FileBaseDTO> findFileBaseDTOByDelTagOfLimit(int delTag, int limit) {
        Query query = new Query();
        query.addCriteria(Criteria.where("delete").is(delTag));
        query.with(Sort.by(Sort.Direction.DESC, Constants.IS_FOLDER));
        query.limit(limit);
        return mongoTemplate.find(query, FileBaseDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public void removeById(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        mongoTemplate.remove(query, FileDocument.class);
    }

    @Override
    public long unsetDelTag(String fileId) {
        Query removeDeletequery = new Query();
        removeDeletequery.addCriteria(Criteria.where("_id").is(fileId).and("delete").is(1));
        Update update = new Update();
        update.unset("delete");
        UpdateResult result = mongoTemplate.updateMulti(removeDeletequery, update, CommonFileService.COLLECTION_NAME);
        return result.getModifiedCount();
    }

    @Override
    public void removeByIdIn(List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        mongoTemplate.remove(query, FileDocument.class);
    }

    @Override
    public List<FileDocument> findAllAndRemoveByIdIn(List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        return mongoTemplate.findAllAndRemove(query, FileDocument.class);
    }

    @Override
    public FileBaseDTO findFileBaseDTOById(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        return mongoTemplate.findOne(query, FileBaseDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public void setIsFavoriteByIdIn(List<String> fileIds, boolean isFavorite) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        Update update = new Update();
        update.set(Constants.IS_FAVORITE, isFavorite);
        mongoTemplate.updateMulti(query, update, FileDocument.class);
    }

    @Override
    public void setNameAndSuffixById(String name, String suffix, String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set(Constants.FILENAME_FIELD, name);
        update.set(Constants.SUFFIX, suffix);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @NotNull
    private Query getUpdateSharePropsQuery(FileDocument file, boolean isFolder) {
        Query query = new Query();
        if (isFolder) {
            query.addCriteria(Criteria.where(USER_ID).is(userLoginHolder.getUserId()));
            query.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(file.getPath() + file.getName() + "/")));
        } else {
            query = new Query();
            query.addCriteria(Criteria.where("_id").is(file.getId()));
        }
        return query;
    }

    /**
     * 设置共享属性
     *
     * @param expiresAt      过期时间
     * @param shareId        shareId
     * @param isPrivacy      isPrivacy
     * @param extractionCode extractionCode
     */
    public static void setShareAttribute(Update update, long expiresAt, String shareId, Boolean isPrivacy, String extractionCode, List<OperationPermission> operationPermissionListList) {
        update.set(Constants.IS_SHARE, true);
        update.set(Constants.SHARE_ID, shareId);
        update.set(Constants.EXPIRES_AT, expiresAt);
        update.set(Constants.IS_PRIVACY, isPrivacy);
        if (operationPermissionListList != null) {
            update.set(Constants.OPERATION_PERMISSION_LIST, operationPermissionListList);
        }
        if (BooleanUtil.isTrue(isPrivacy)) {
            update.set(Constants.EXTRACTION_CODE, extractionCode);
        } else {
            update.unset(Constants.EXTRACTION_CODE);
        }
    }
}
