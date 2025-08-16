package com.jmal.clouddisk.oss.web;

import cn.hutool.core.convert.Convert;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.oss.BaseOssService;
import com.jmal.clouddisk.oss.BucketInfo;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.CommonUserFileService;
import com.jmal.clouddisk.service.impl.CommonUserService;
import com.jmal.clouddisk.service.impl.MessageService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description WebOssCommonService
 * @date 2023/4/14 10:22
 */
@Service
public class WebOssCommonService {

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    CommonUserService userService;

    @Autowired
    CommonUserFileService commonUserFileService;

    @Autowired
    MessageService messageService;

    @Autowired
    CommonFileService commonFileService;

    public void notifyCreateFile(String username, String objectName, String ossRootFolderName) {
        FileIntroVO fileIntroVO = new FileIntroVO();
        fileIntroVO.setPath(getPathByObjectName(ossRootFolderName, objectName));
        fileIntroVO.setName(Paths.get(objectName).getFileName().toString());
        messageService.pushMessage(username, fileIntroVO, Constants.CREATE_FILE);
    }

    public void notifyUpdateFile(String ossPath, String objectName, long size) {
        FileIntroVO fileIntroVO = new FileIntroVO();
        String username = getUsernameByOssPath(ossPath);
        String id = getFileId(getOssRootFolderName(ossPath), objectName, username);
        fileIntroVO.setId(id);
        fileIntroVO.setSize(size);
        fileIntroVO.setUpdateDate(LocalDateTime.now());
        messageService.pushMessage(username, fileIntroVO, Constants.UPDATE_FILE);
    }

    public void notifyDeleteFile(String ossPath, String objectName) {
        String username = getUsernameByOssPath(ossPath);
        String id = getFileId(getOssRootFolderName(ossPath), objectName, username);
        String filename = Paths.get(objectName).getFileName().toString();
        String path = id.substring(username.length(), id.length() - filename.length());
        messageService.pushMessage(username, path, Constants.DELETE_FILE);
    }

    public static String getFileId(String rootName, String objectName, String username) {
        boolean isFolder = objectName.endsWith("/");
        return Paths.get(username, rootName, objectName) + (isFolder ? MyWebdavServlet.PATH_DELIMITER : "");
    }

    public static String getPathByObjectName(String ossRootFolderName, String objectName) {
        String path = MyWebdavServlet.PATH_DELIMITER;
        Path filePath = Paths.get(ossRootFolderName, objectName);
        if (filePath.getNameCount() > 1) {
            path += filePath.getParent().toString() + MyWebdavServlet.PATH_DELIMITER;
        }
        return path;
    }

    public static String getUsernameByOssPath(String ossPath) {
        return Paths.get(ossPath).subpath(0, 1).toString();
    }

    public static String getOssRootFolderName(String ossPath) {
        return CaffeineUtil.getOssDiameterPrefixCache(ossPath).getFolderName();
    }

    public static String getPath(String objectName, String rootName) {
        String path;
        Path keyPath = Paths.get(objectName);
        if (keyPath.getNameCount() > 1) {
            path = MyWebdavServlet.PATH_DELIMITER + Paths.get(rootName, objectName).getParent().toString() + MyWebdavServlet.PATH_DELIMITER;
        } else {
            path = MyWebdavServlet.PATH_DELIMITER + rootName + MyWebdavServlet.PATH_DELIMITER;
        }
        return path;
    }

    private FileDocument getFileDocument(String ossPathTo, String objectName) {
        BucketInfo bucketInfo = CaffeineUtil.getOssDiameterPrefixCache(ossPathTo);
        FileInfo fileInfo = BaseOssService.newFileInfo(objectName, bucketInfo.getBucketName());
        String username = getUsernameByOssPath(ossPathTo);
        return fileInfo.toFileDocument(ossPathTo, userService.getUserIdByUserName(username));
    }

    public void afterUploadComplete(String objectName, String ossPath, FileDocument fileDocument) {
        if (fileDocument == null) {
            fileDocument = getFileDocument(ossPath, objectName);
        }
        String rootName = getOssRootFolderName(ossPath);
        Update update = new Update();
        commonUserFileService.checkShareBase(update, getPath(objectName, rootName));
        Document updateObject = update.getUpdateObject();
        if (updateObject.get("$set") != null) {
            Document document = updateObject.get("$set", Document.class);
            if (document.get(Constants.IS_SHARE) != null) {
                fileDocument.setIsShare(document.getBoolean(Constants.IS_SHARE));
            }
            if (document.get(Constants.SHARE_ID) != null) {
                fileDocument.setShareId(document.getString(Constants.SHARE_ID));
            }
            if (document.get(Constants.EXPIRES_AT) != null) {
                fileDocument.setExpiresAt(document.getLong(Constants.EXPIRES_AT));
            }
            if (document.get(Constants.IS_PRIVACY) != null) {
                fileDocument.setIsPrivacy(document.getBoolean(Constants.IS_PRIVACY));
            }
            if (document.get(Constants.EXTRACTION_CODE) != null) {
                fileDocument.setExtractionCode(document.getString(Constants.EXTRACTION_CODE));
            }
            if (document.get(Constants.OPERATION_PERMISSION_LIST) != null) {
                fileDocument.setOperationPermissionList(Convert.toList(OperationPermission.class, document.get(Constants.OPERATION_PERMISSION_LIST)));
            }
        }
        mongoTemplate.save(fileDocument);
    }

}
