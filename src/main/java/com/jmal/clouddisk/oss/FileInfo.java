package com.jmal.clouddisk.oss;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.oss.web.WebOssCommonService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.MyFileUtils;
import lombok.Data;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class FileInfo implements Reflective {
    private String bucketName;
    private String key;
    private String eTag;
    private long size;
    private Date lastModified;

    public FileInfo() {
    }

    public FileInfo(String key, String eTag, long size, Date lastModified) {
        this.key = key;
        this.eTag = eTag;
        this.size = size;
        this.lastModified = lastModified;
    }

    public String getName() {
        Path path = Paths.get(key);
        return path.getFileName().toString();
    }

    public boolean isFolder() {
        return key.endsWith("/");
    }

    public FileIntroVO toFileIntroVO(String ossPath, String userId) {
        BucketInfo bucketInfo = CaffeineUtil.getOssDiameterPrefixCache(ossPath);
        String username = Paths.get(ossPath).subpath(0, 1).toString();
        String rootName = bucketInfo.getFolderName();
        FileIntroVO fileIntroVO = new FileIntroVO();
        String fileName = getName();
        fileIntroVO.setId(WebOssCommonService.getFileId(rootName, key, username));
        fileIntroVO.setUsername(username);
        fileIntroVO.setIsFavorite(false);
        fileIntroVO.setIsFolder(isFolder());
        fileIntroVO.setName(fileName);
        fileIntroVO.setPath(WebOssCommonService.getPath(key, rootName));
        fileIntroVO.setSize(size);
        String suffix = MyFileUtils.extName(fileName);
        fileIntroVO.setSuffix(suffix);
        fileIntroVO.setMd5(eTag);
        fileIntroVO.setContentType(FileContentTypeUtils.getContentType(suffix));
        if (lastModified != null) {
            LocalDateTime updateTime = LocalDateTimeUtil.of(lastModified);
            fileIntroVO.setAgoTime(System.currentTimeMillis() - lastModified.getTime());
            fileIntroVO.setUploadDate(updateTime);
            fileIntroVO.setUpdateDate(updateTime);
        }
        fileIntroVO.setUserId(userId);
        return fileIntroVO;
    }

    public FileDocument toFileDocument(String ossPath, String userId) {
        FileDocument fileDocument = new FileDocument();
        FileIntroVO fileIntroVO = toFileIntroVO(ossPath, userId);
        fileDocument.setAgoTime(fileIntroVO.getAgoTime());
        fileDocument.setId(fileIntroVO.getId());
        fileDocument.setUsername(fileDocument.getUsername());
        fileDocument.setIsFavorite(fileIntroVO.getIsFavorite());
        fileDocument.setIsFolder(fileIntroVO.getIsFolder());
        fileDocument.setName(fileIntroVO.getName());
        fileDocument.setPath(fileIntroVO.getPath());
        fileDocument.setSize(fileIntroVO.getSize());
        fileDocument.setSuffix(fileIntroVO.getSuffix());
        fileDocument.setMd5(fileIntroVO.getMd5());
        fileDocument.setContentType(fileIntroVO.getContentType());
        fileDocument.setUploadDate(fileIntroVO.getUploadDate());
        fileDocument.setUpdateDate(fileIntroVO.getUpdateDate());
        fileDocument.setUserId(userId);
        return fileDocument;
    }
}
