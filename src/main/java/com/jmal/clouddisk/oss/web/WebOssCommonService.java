package com.jmal.clouddisk.oss.web;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
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
@Slf4j
public class WebOssCommonService {

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    FileProperties fileProperties;

    @Autowired
    IUserService userService;

    @Autowired
    CommonFileService commonFileService;

    public void notifyCreateFile(String username, String objectName, String ossRootFolderName) {
        FileIntroVO fileIntroVO = new FileIntroVO();
        fileIntroVO.setPath(getPathByObjectName(ossRootFolderName, objectName));
        commonFileService.pushMessage(username, fileIntroVO, "createFile");
    }

    public void notifyUpdateFile(String ossPath, String objectName, long size) {
        FileIntroVO fileIntroVO = new FileIntroVO();
        String username = getUsernameByOssPath(ossPath);
        String id = getFileId(getOssRootFolderName(ossPath), objectName, username);
        fileIntroVO.setId(id);
        fileIntroVO.setSize(size);
        fileIntroVO.setUpdateDate(LocalDateTime.now());
        commonFileService.pushMessage(username, fileIntroVO, "updateFile");
    }

    public void notifyDeleteFile(String ossPath, String objectName) {
        FileIntroVO fileIntroVO = new FileIntroVO();
        String username = getUsernameByOssPath(ossPath);
        String id = getFileId(getOssRootFolderName(ossPath), objectName, username);
        fileIntroVO.setId(id);
        commonFileService.pushMessage(username, fileIntroVO, "deleteFile");
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

}
