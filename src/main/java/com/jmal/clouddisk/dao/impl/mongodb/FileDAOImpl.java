package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.lucene.IndexStatus;
import com.jmal.clouddisk.lucene.LuceneService;
import com.jmal.clouddisk.media.TranscodeConfig;
import com.jmal.clouddisk.media.TranscodeStatus;
import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.media.VideoProcessService;
import com.jmal.clouddisk.model.ShareBaseInfoDTO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.OtherProperties;
import com.jmal.clouddisk.model.file.ShareProperties;
import com.jmal.clouddisk.model.file.dto.*;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.MongoUtil;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.jmal.clouddisk.service.IUserService.USER_ID;
import static com.jmal.clouddisk.service.impl.CommonFileService.COLLECTION_NAME;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class FileDAOImpl implements IFileDAO {

    private final MongoTemplate mongoTemplate;

    private final UserLoginHolder userLoginHolder;

    @Override
    public void deleteAllByIdInBatch(List<String> userIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).in(userIdList));
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
        mongoTemplate.updateMulti(query1, update, FileDocument.class);
    }

    /**
     * 获取文件夹下的子分享的查询条件
     *
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
    public String save(FileDocument file) {
        FileDocument saved = mongoTemplate.save(file);
        return saved.getId();
    }

    @Override
    public String upsertMountFile(FileDocument fileDocument) {
        Update update = MongoUtil.getUpdate(fileDocument);
        update.set("remark", "挂载 mount");
        Query query = getQuery(fileDocument.getUserId(), fileDocument.getPath(), fileDocument.getName());
        UpdateResult updateResult = mongoTemplate.upsert(query, update, FileDocument.class);
        if (updateResult.getUpsertedId() != null) {
            return updateResult.getUpsertedId().asString().getValue();
        }
        return null;
    }

    private static Query getMountQuery(String fileId, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.MOUNT_FILE_ID_FIELD).is(fileId));
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
        query.addCriteria(Criteria.where(Constants.MOUNT_FILE_ID_FIELD).is(fileId));
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
        update.set(Constants.IS_SHARE, true);
        update.set(Constants.SHARE_ID, shareId);
        update.set(Constants.EXPIRES_AT, shareProperties.getExpiresAt());
        update.set(Constants.IS_PRIVACY, shareProperties.getIsPrivacy());
        if (shareProperties.getOperationPermissionList() != null) {
            update.set(Constants.OPERATION_PERMISSION_LIST, shareProperties.getOperationPermissionList());
        }
        if (BooleanUtil.isTrue(shareProperties.getIsPrivacy())) {
            update.set(Constants.EXTRACTION_CODE, shareProperties.getExtractionCode());
        } else {
            update.unset(Constants.EXTRACTION_CODE);
        }
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
    public FileDocument findByUserIdAndPathAndName(String userId, String path, String name, String... includeFields) {
        Query query = getQuery(userId, path, name);
        query.fields().exclude(includeFields);
        boolean readContent = false;
        for (String field : includeFields) {
            if (Constants.CONTENT.equals(field)) {
                readContent = true;
                break;
            }
        }
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        if (fileDocument == null) {
            return null;
        }
        if (readContent && fileDocument.getContent() != null) {
            fileDocument.setInputStream(new ByteArrayInputStream(fileDocument.getContent()));
        }
        return fileDocument;
    }

    @Override
    public FileBaseDTO findFileBaseDTOByUserIdAndPathAndName(String userId, String relativePath, String fileName) {
        Query query = getQuery(userId, relativePath, fileName);
        return mongoTemplate.findOne(query, FileBaseDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public String findIdByUserIdAndPathAndName(String userId, String path, String name) {
        Query query = getQuery(userId, path, name);
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
        Query query = getQuery(userId, path, name);
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

    @Override
    public void setContent(String id, byte[] content) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        Update update = new Update();
        update.set(Constants.CONTENT, content);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public void setMediaCover(String id, Boolean mediaCover) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        Update update = new Update();
        update.set("mediaCover", mediaCover);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public void setShowCover(String id, Boolean showCover) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        Update update = new Update();
        update.set("showCover", showCover);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public List<FileBaseDTO> findAllFileBaseDTOByIdIn(List<String> fileIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList));
        return mongoTemplate.find(query, FileBaseDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public List<FileBaseDTO> findAllByUserIdAndPathPrefix(String userId, String pathPrefix) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").regex("^" + pathPrefix));
        return mongoTemplate.find(query, FileBaseDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public void setPathById(String id, String newFilePath) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        Update update = new Update();
        update.set(Constants.PATH_FIELD, newFilePath);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public List<FileDocument> findAllByUserIdAndPathAndNameIn(String userId, String toPath, List<String> fromFilenameList) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where(Constants.PATH_FIELD).is(toPath));
        query.addCriteria(Criteria.where(Constants.FILENAME_FIELD).in(fromFilenameList));
        return mongoTemplate.find(query, FileDocument.class);
    }

    @Override
    public List<String> findFilenameListByIdIn(List<String> ids) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(ids));
        query.fields().include("name");
        return mongoTemplate.find(query, FileDocument.class).stream().map(FileDocument::getName).toList();
    }

    @Override
    public List<FileBaseAllDTO> findAllFileBaseAllDTOByUserIdAndPath(String userId, String path) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where(Constants.PATH_FIELD).is(path));
        return mongoTemplate.find(query, FileBaseAllDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public void removeTagsByTagIdIn(List<String> removeTagIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("tags.tagId").in(removeTagIds));
        List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class);
        fileDocumentList.parallelStream().forEach(fileDocument -> {
            List<Tag> tagList = fileDocument.getTags();
            tagList.removeIf(tagDTO -> removeTagIds.contains(tagDTO.getTagId()));
            Update update = new Update();
            update.set("tags", mongoTemplate.getConverter().convertToMongoType(tagList));
            mongoTemplate.updateMulti(query, update, FileDocument.class);
        });
    }

    @Override
    public List<String> getFileIdListByTagId(String tagId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("tags.tagId").is(tagId));
        query.fields().include("_id");
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class);
        return list.stream().map(FileDocument::getId).toList();
    }

    @Override
    public void setTagsByIdIn(List<String> fileIds, List<Tag> tagList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        Update update = new Update();
        update.set("tags", mongoTemplate.getConverter().convertToMongoType(tagList));
        mongoTemplate.updateMulti(query, update, FileDocument.class);
    }

    @Override
    public void setNameByMountFileId(String fileId, String newFileName) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.MOUNT_FILE_ID_FIELD).is(fileId));
        Update update = new Update();
        update.set(Constants.FILENAME_FIELD, newFileName);
        mongoTemplate.updateMulti(query, update, FileDocument.class);
    }

    @Override
    public ShareBaseInfoDTO getShareBaseByPath(String relativePath) {
        Path path = Paths.get(relativePath);
        StringBuilder pathStr = new StringBuilder("/");
        List<Document> documentList = new ArrayList<>(path.getNameCount());
        for (int i = 0; i < path.getNameCount(); i++) {
            String filename = path.getName(i).toString();
            if (i > 0) {
                pathStr.append("/");
            }
            Document document = new Document("path", pathStr.toString()).append("name", filename);
            documentList.add(document);
            pathStr.append(filename);
        }
        if (documentList.isEmpty()) {
            return null;
        }
        List<Document> list = Arrays.asList(new Document("$match", new Document("$or", documentList)), new Document("$match", new Document(Constants.SHARE_BASE, true)));
        AggregateIterable<Document> result = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(list);
        Document shareDocument = null;
        try (MongoCursor<Document> mongoCursor = result.iterator()) {
            while (mongoCursor.hasNext()) {
                shareDocument = mongoCursor.next();
            }
        }
        if (shareDocument == null) {
            return null;
        }
        return mongoTemplate.getConverter().read(ShareBaseInfoDTO.class, shareDocument);
    }

    @Override
    public void updateFileByUserIdAndPathAndName(String userId, String path, String name, UpdateFile updateFile) {
        Query query = getQuery(userId, path, name);
        Update update = new Update();
        if (updateFile.getExif() != null) {
            update.set("exif", updateFile.getExif());
        }
        if (updateFile.getSuffix() != null) {
            update.set(Constants.SUFFIX, updateFile.getSuffix());
        }
        if (updateFile.getVideo() != null) {
            VideoInfoDO videoInfoDO = updateFile.getVideo();
            update.set("video.bitrate", videoInfoDO.getBitrate());
            update.set("video.bitrateNum", videoInfoDO.getBitrateNum());
            update.set("video.format", videoInfoDO.getFormat());
            update.set("video.duration", videoInfoDO.getDuration());
            update.set("video.durationNum", videoInfoDO.getDurationNum());
            update.set("video.width", videoInfoDO.getWidth());
            update.set("video.height", videoInfoDO.getHeight());
            update.set("video.frameRate", videoInfoDO.getFrameRate());
        }
        if (updateFile.getContentType() != null) {
            update.set(Constants.CONTENT_TYPE, updateFile.getContentType());
        }
        if (updateFile.getUpdateDate() != null) {
            update.set(Constants.UPDATE_DATE, updateFile.getUpdateDate());
        }
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public String upsertByUserIdAndPathAndName(String userId, String relativePath, String fileName, FileDocument fileDocument) {
        Update update = MongoUtil.getUpdate(fileDocument);
        update.set("_id", new ObjectId(fileDocument.getId()));
        Query query = getQuery(userId, relativePath, fileName);
        UpdateResult updateResult = mongoTemplate.upsert(query, update, FileDocument.class);
        if (updateResult.getUpsertedId() != null) {
            return updateResult.getUpsertedId().asObjectId().getValue().toHexString();
        }
        return null;
    }

    @Override
    public void setUpdateDateById(String fileId, LocalDateTime time) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set(Constants.UPDATE_DATE, time);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public List<FileDocument> findByPath(String path) {
        Query query = new Query();
        query.addCriteria(Criteria.where("path").is(path));
        query.fields().exclude(Constants.CONTENT, Constants.CONTENT_DRAFT, Constants.CONTENT_TEXT, Constants.CONTENT_HTML);
        return mongoTemplate.find(query, FileDocument.class);
    }

    @Override
    public List<FileDocument> findAllAndRemoveByPathPrefix(String pathName) {
        Query query = new Query();
        query.addCriteria(Criteria.where("path").regex("^" + pathName));
        return mongoTemplate.findAllAndRemove(query, FileDocument.class);
    }

    @Override
    public List<FileDocument> findAllAndRemoveByMountFileIdPrefix(String pathName) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.MOUNT_FILE_ID_FIELD).regex("^" + pathName));
        return mongoTemplate.findAllAndRemove(query, FileDocument.class);
    }

    @Override
    public List<String> findIdsAndRemoveByIdPrefix(String pathName) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").regex("^" + pathName));
        query.fields().include("_id");
        List<FileDocument> list = mongoTemplate.findAllAndRemove(query, FileDocument.class);
        return list.stream().map(FileDocument::getId).toList();
    }

    @Override
    public FileDocument findThumbnailContentInputStreamById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        query.fields().include(Constants.CONTENT);
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        if (fileDocument == null || fileDocument.getContent() == null) {
            return null;
        }
        fileDocument.setInputStream(new ByteArrayInputStream(fileDocument.getContent()));
        return fileDocument;
    }

    @Override
    public FileBaseOperationPermissionDTO findFileBaseOperationPermissionDTOById(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        return mongoTemplate.findOne(query, FileBaseOperationPermissionDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public void unsetTranscodeVideo() {
        Query query = new Query();
        query.addCriteria(Criteria.where(VideoProcessService.TRANSCODE_VIDEO).is(TranscodeStatus.NOT_TRANSCODE.getStatus()));
        Update update = new Update();
        update.unset(VideoProcessService.TRANSCODE_VIDEO);
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public long updateTranscodeVideoByIdIn(List<String> fileIdList, int status) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList));
        Update update = new Update();
        update.set(VideoProcessService.TRANSCODE_VIDEO, status);
        UpdateResult updateResult = mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
        return updateResult.getModifiedCount();
    }

    @Override
    public long countNotTranscodeVideo() {
        Query query = new Query();
        query.addCriteria(Criteria.where(VideoProcessService.TRANSCODE_VIDEO).is(TranscodeStatus.NOT_TRANSCODE.getStatus()));
        return mongoTemplate.count(query, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public List<FileBaseDTO> findFileBaseDTOByNotTranscodeVideo() {
        Query query = new Query();
        query.addCriteria(Criteria.where(VideoProcessService.TRANSCODE_VIDEO).is(TranscodeStatus.NOT_TRANSCODE.getStatus()));
        return mongoTemplate.find(query, FileBaseDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public VideoInfoDO findVideoInfoById(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        query.fields().include("video");
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        if (fileDocument == null) {
            return null;
        }
        return fileDocument.getVideo();
    }

    @Override
    public void setTranscodeVideoInfoByUserIdAndPathAndName(OtherProperties otherProperties, String userId, String path, String name) {
        Query query = getQuery(userId, path, name);
        Update update = new Update();
        update.set("m3u8", otherProperties.getM3u8());
        update.set("vtt", otherProperties.getVtt());
        if (otherProperties.getVideo() != null) {
            update.set("video.toHeight", otherProperties.getVideo().getHeight());
            update.set("video.toBitrate", otherProperties.getVideo().getBitrate());
            update.set("video.toFrameRate", otherProperties.getVideo().getFrameRate());
        }
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public List<String> findTranscodeConfigIds(TranscodeConfig config) {
        List<Bson> pipeline = Arrays.asList(new Document("$match",
                        new Document("video",
                                new Document("$exists", true))),
                new Document("$match",
                        new Document("$or", Arrays.asList(
                                new Document("video.height",
                                        new Document("$exists", false)),
                                new Document("video.height",
                                        new Document("$gt", config.getHeightCond())),
                                new Document("video.bitrateNum",
                                        new Document("$gt", config.getBitrateCond() * 1000)),
                                new Document("video.frameRate",
                                        new Document("$gt", config.getFrameRateCond()))))),
                new Document("$match",
                        new Document("$or", Arrays.asList(new Document("video.toHeight",
                                        new Document("$ne", config.getHeight())),
                                new Document("video.toBitrate",
                                        new Document("$ne", config.getBitrate())),
                                new Document("video.toFrameRate",
                                        new Document("$ne", config.getFrameRate()))))),
                new Document("$project",
                        new Document("_id", 1L)));
        List<String> fileIdList = new ArrayList<>();
        AggregateIterable<Document> aggregateIterable = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(pipeline);
        for (org.bson.Document document : aggregateIterable) {
            String fileId = document.getObjectId("_id").toHexString();
            fileIdList.add(fileId);
        }
        return fileIdList;
    }

    @Override
    public void updateLuceneIndexStatusByIdIn(List<String> fileIdList, int indexStatus) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList));
        Update update = new Update();
        update.set(LuceneService.MONGO_INDEX_FIELD, indexStatus);
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
    }

    @Override
    public long countByLuceneIndex(int status) {
        Query query = new Query();
        query.addCriteria(Criteria.where(LuceneService.MONGO_INDEX_FIELD).is(status));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    @Override
    public List<FileBaseLuceneDTO> findFileBaseLuceneDTOByLuceneIndex(int status, int limit) {
        Query query = new Query();
        query.addCriteria(Criteria.where(LuceneService.MONGO_INDEX_FIELD).is(status));
        query.limit(limit);
        return mongoTemplate.find(query, FileBaseLuceneDTO.class, COLLECTION_NAME);
    }

    @Override
    public List<FileBaseLuceneDTO> findFileBaseLuceneDTOByIdIn(List<String> fileIdList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList));
        return mongoTemplate.find(query, FileBaseLuceneDTO.class, COLLECTION_NAME);
    }

    @Override
    public void UnsetDelTagByIdIn(List<String> fileIdList) {
        Query query = new Query();
        if (fileIdList == null || fileIdList.isEmpty()) {
            query.addCriteria(Criteria.where("delete").is(1));
        } else {
            query.addCriteria(Criteria.where("_id").in(fileIdList).and("delete").is(1));
        }
        Update update = new Update();
        update.unset("delete");
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public void setDelTag(String userId, String path) {
        Query query = new Query();
        query.addCriteria(Criteria.where("alonePage").exists(false));
        query.addCriteria(Criteria.where("release").exists(false));
        query.addCriteria(Criteria.where(Constants.MOUNT_FILE_ID_FIELD).exists(false));
        if (StrUtil.isNotBlank(userId)) {
            query.addCriteria(Criteria.where("userId").is(userId));
        }
        if (StrUtil.isNotBlank(path)) {
            query.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(path)));
        }
        Update update = new Update();
        // 添加删除标记用于在之后删除
        update.set("delete", 1);
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public boolean existsByUnIndexed() {
        Query query = new Query();
        query.addCriteria(Criteria.where(LuceneService.MONGO_INDEX_FIELD).lte(IndexStatus.INDEXING.getStatus()));
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public void resetIndexStatus() {
        Query query = new Query();
        query.addCriteria(Criteria.where(LuceneService.MONGO_INDEX_FIELD).lte(IndexStatus.INDEXING.getStatus()));
        Update update = new Update();
        update.unset(LuceneService.MONGO_INDEX_FIELD);
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public long countOssFolder() {
        Query query = new Query();
        query.addCriteria(Criteria.where("ossFolder").exists(true));
        return mongoTemplate.count(query, FileDocument.class);
    }

    @Override
    public List<FileBaseDTO> findMountFileBaseDTOByUserId(String userId) {
        List<String> mountFileIdList = findMountFileIdByUserId(userId);
        return findAllFileBaseDTOByIdIn(mountFileIdList);
    }

    public List<String> findMountFileIdByUserId(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USER_ID).is(userId));
        query.addCriteria(Criteria.where(Constants.MOUNT_FILE_ID_FIELD).exists(true));
        query.fields().include(Constants.MOUNT_FILE_ID_FIELD);
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class);
        return list.stream().map(FileDocument::getMountFileId).distinct().toList();
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

    public static Query getQuery(String userId, String path, String name) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where(Constants.PATH_FIELD).is(path));
        query.addCriteria(Criteria.where(Constants.FILENAME_FIELD).is(name));
        return query;
    }

}
