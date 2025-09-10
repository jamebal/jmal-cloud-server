package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.oss.web.WebOssCommonService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.CaffeineUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final CommonUserService userService;

    private final MongoTemplate mongoTemplate;

    public FileDocument getFileDocumentById(String fileId, boolean excludeContent) {
        if (CharSequenceUtil.isBlank(fileId) || Constants.REGION_DEFAULT.equals(fileId)) {
            return null;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        if (excludeContent) {
            query.fields().exclude(Constants.CONTENT);
        }
        query.fields().exclude(Constants.CONTENT_TEXT);
        return mongoTemplate.findOne(query, FileDocument.class);
    }

    public FileDocument getById(String fileId) {
        return getById(fileId, true);
    }

    public FileDocument getById(String fileId, boolean excludeContent) {
        FileDocument fileDocument = getFileDocumentById(fileId, excludeContent);
        if (fileDocument != null) {
            return fileDocument;
        }
        String ossPath = CaffeineUtil.getOssPath(Paths.get(fileId));
        if (ossPath != null) {
            return getFileDocumentByOssPath(ossPath, fileId);
        }
        return null;
    }

    public FileDocument getFileDocumentByOssPath(String ossPath, String pathName) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = pathName.substring(ossPath.length());
        try (AbstractOssObject abstractOssObject = ossService.getAbstractOssObject(objectName)) {
            if (abstractOssObject == null) {
                return null;
            }
            FileInfo fileInfo = abstractOssObject.getFileInfo();
            String username = WebOssCommonService.getUsernameByOssPath(ossPath);
            String userId = userService.getUserIdByUserName(username);
            return fileInfo.toFileDocument(ossPath, userId);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

}
