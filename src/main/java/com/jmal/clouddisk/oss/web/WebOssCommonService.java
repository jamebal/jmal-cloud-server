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

    public void notifyDeleteFile(String ossPath, String objectName) {
        FileIntroVO fileIntroVO = new FileIntroVO();
        String username = getUsernameByOssPath(ossPath);
        String rootName = getOssRootFolderName(ossPath);
        boolean isFolder = objectName.endsWith("/");
        fileIntroVO.setId(Paths.get(username, rootName, objectName) + (isFolder ? MyWebdavServlet.PATH_DELIMITER : ""));
        commonFileService.pushMessage(username, fileIntroVO, "deleteFile");
    }

    public static String getPathByObjectName(String ossRootFolderName, String objectName) {
        String path = MyWebdavServlet.PATH_DELIMITER;
        Path filePath = Paths.get(ossRootFolderName, objectName);
        if (filePath.getNameCount() > 1) {
            path += filePath.getParent().toString() + MyWebdavServlet.PATH_DELIMITER;
        }
        return path;
    }

    public String getUsernameByOssPath(String ossPath) {
        return Paths.get(ossPath).subpath(0, 1).toString();
    }

    public static String getOssRootFolderName(String ossPath) {
        return CaffeineUtil.getOssDiameterPrefixCache(ossPath).getFolderName();
    }
}
