package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IFileQueryDAO;
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
import org.springframework.stereotype.Service;

import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final CommonUserService userService;

    private final IFileQueryDAO fileQueryDAO;

    public FileDocument getFileDocumentById(String fileId, boolean excludeContent) {
        if (CharSequenceUtil.isBlank(fileId) || Constants.REGION_DEFAULT.equals(fileId)) {
            return null;
        }
        return fileQueryDAO.findBaseFileDocumentById(fileId, excludeContent);
    }

    public FileDocument getById(String fileId) {
        return getById(fileId, true);
    }

    public FileDocument getById(String fileId, boolean excludeContent) {
        String ossPath = CaffeineUtil.getOssPath(Paths.get(fileId));
        if (ossPath != null) {
            FileDocument fileDocument = getFileDocumentById(fileId, excludeContent);
            if (fileDocument != null) {
                return fileDocument;
            }
            return getFileDocumentByOssPath(ossPath, fileId);
        }
        return getFileDocumentById(fileId, excludeContent);
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
