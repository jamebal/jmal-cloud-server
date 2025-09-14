package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileQueryDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.model.file.dto.FileBaseMountDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.FileSortService;
import com.jmal.clouddisk.service.impl.MessageService;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.jmal.clouddisk.service.IUserService.USER_ID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class FileQueryDAOImpl implements IFileQueryDAO {

    private final MongoTemplate mongoTemplate;
    private final FileProperties fileProperties;
    private final MessageService messageService;


    @Override
    public Page<FileIntroVO> getFileIntroVO(UploadApiParamDTO upload) {
        String currentDirectory = upload.getCurrentDirectory();
        Criteria criteria;
        String queryFileType = upload.getQueryFileType();
        if (CharSequenceUtil.isNotBlank(queryFileType)) {
            criteria = switch (upload.getQueryFileType()) {
                case Constants.AUDIO -> Criteria.where(Constants.CONTENT_TYPE).regex("^" + Constants.AUDIO);
                case Constants.VIDEO -> Criteria.where(Constants.CONTENT_TYPE).regex("^" + Constants.VIDEO);
                case Constants.CONTENT_TYPE_IMAGE -> Criteria.where(Constants.CONTENT_TYPE).regex("^image");
                case "text" -> Criteria.where(Constants.SUFFIX).in(fileProperties.getSimText());
                case Constants.DOCUMENT -> Criteria.where(Constants.SUFFIX).in(fileProperties.getDocument());
                case CommonFileService.TRASH_COLLECTION_NAME -> new Criteria();
                default -> Criteria.where("path").is(currentDirectory);
            };
        } else {
            criteria = Criteria.where("path").is(currentDirectory);
            if (currentDirectory.length() < 2) {
                Boolean isFolder = upload.getIsFolder();
                if (isFolder != null) {
                    criteria = Criteria.where(Constants.IS_FOLDER).is(isFolder);
                }
                if (BooleanUtil.isTrue(upload.getIsFavorite())) {
                    criteria = Criteria.where(Constants.IS_FAVORITE).is(upload.getIsFavorite());
                }
                if (BooleanUtil.isTrue(upload.getIsMount())) {
                    criteria = Criteria.where("mountFileId").exists(true);
                }
                String tagId = upload.getTagId();
                if (CharSequenceUtil.isNotBlank(tagId)) {
                    criteria = Criteria.where("tags.tagId").is(tagId);
                }
            }
        }
        long count = getFileDocumentsCount(upload, criteria);

        List<FileIntroVO> list = getFileDocuments(upload, criteria);
        return new PageImpl<>(list, upload.getPageable(), count);
    }

    @Override
    public List<FileBaseMountDTO> getDirDocuments(UploadApiParamDTO upload) {
        Criteria criteria = Criteria.where("path").is(upload.getCurrentDirectory());
        Query query = getQuery(upload, criteria);
        query.addCriteria(Criteria.where(Constants.IS_FOLDER).is(true));
        return mongoTemplate.find(query, FileBaseMountDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public FileDocument findBaseFileDocumentById(String id, boolean excludeContent) {
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(id));
        query.fields().exclude(Constants.CONTENT_DRAFT, Constants.CONTENT_TEXT, Constants.CONTENT_HTML);
        if (excludeContent) {
            query.fields().exclude(Constants.CONTENT);
        }
        return mongoTemplate.findOne(query, FileDocument.class, CommonFileService.COLLECTION_NAME);
    }

    /***
     * 通过查询条件获取文件数
     * @param upload UploadApiParamDTO
     * @param criteria Criteria
     * @return 文件数
     */
    private long getFileDocumentsCount(UploadApiParamDTO upload, Criteria criteria) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USER_ID).is(upload.getUserId()));
        query.addCriteria(criteria);
        String collectionName = BooleanUtil.isTrue(upload.getIsTrash()) ? CommonFileService.TRASH_COLLECTION_NAME : CommonFileService.COLLECTION_NAME;
        if (CommonFileService.TRASH_COLLECTION_NAME.equals(collectionName)) {
            query.addCriteria(Criteria.where("hidden").is(false));
        }
        return mongoTemplate.count(query, collectionName);
    }

    private List<FileIntroVO> getFileDocuments(UploadApiParamDTO upload, Criteria criteria) {
        List<FileIntroVO> fileIntroVOList;
        Query query = getQuery(upload, criteria);
        String order = listByPage(upload, query);
        if (!CharSequenceUtil.isBlank(order)) {
            String sortableProp = upload.getSortableProp();
            Sort.Direction direction = Sort.Direction.ASC;
            if (Constants.DESCENDING.equals(order)) {
                direction = Sort.Direction.DESC;
            }
            query.with(Sort.by(direction, sortableProp));
        } else {
            query.with(Sort.by(Sort.Direction.DESC, Constants.IS_FOLDER));
        }
        query.fields().exclude(Constants.CONTENT).exclude("music.coverBase64").exclude(Constants.CONTENT_TEXT);
        String collectionName = BooleanUtil.isTrue(upload.getIsTrash()) ? CommonFileService.TRASH_COLLECTION_NAME : CommonFileService.COLLECTION_NAME;
        if (CommonFileService.TRASH_COLLECTION_NAME.equals(collectionName)) {
            query.addCriteria(Criteria.where("hidden").is(false));
        }
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, collectionName);
        long now = System.currentTimeMillis();
        fileIntroVOList = list.parallelStream().map(fileDocument -> {
            LocalDateTime updateDate = fileDocument.getUpdateDate();
            long update = TimeUntils.getMilli(updateDate);
            fileDocument.setAgoTime(now - update);
            FileIntroVO fileIntroVO = new FileIntroVO();
            BeanUtils.copyProperties(fileDocument, fileIntroVO);
            return fileIntroVO;
        }).toList();
        pushConfigInfo(upload);
        return FileSortService.sortByFileName(upload, fileIntroVOList, order);
    }

    private Query getQuery(UploadApiParamDTO upload, Criteria criteria) {
        String userId = upload.getUserId();
        if (CharSequenceUtil.isBlank(userId)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg() + USER_ID);
        }
        Query query = new Query();
        query.addCriteria(Criteria.where(USER_ID).is(userId));
        query.addCriteria(criteria);
        return query;
    }

    private void pushConfigInfo(UploadApiParamDTO upload) {
        messageService.pushMessage(upload.getUsername(), Constants.LOCAL_CHUNK_SIZE, Constants.UPLOADER_CHUNK_SIZE);
    }

    /***
     * 设置分页条件
     * @return 排序条件
     */
    public static String listByPage(UploadApiParamDTO upload, Query query) {
        Integer pageSize = upload.getPageSize();
        Integer pageIndex = upload.getPageIndex();
        if (pageSize != null && pageIndex != null) {
            long skip = (pageIndex - 1L) * pageSize;
            query.skip(skip);
            query.limit(pageSize);
        }
        return upload.getOrder();
    }

}
