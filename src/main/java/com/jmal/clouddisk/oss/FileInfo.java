package com.jmal.clouddisk.oss;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import lombok.Data;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class FileInfo {
    private String bucketName;
    private String key;
    private String eTag;
    private long size;
    private Date lastModified;

    public String getName() {
        Path path = Paths.get(key);
        return path.getFileName().toString();
    }

    public boolean isFolder() {
        return key.endsWith("/");
    }

    public FileIntroVO toFileIntroVO(String ossPath) {
        BucketInfo bucketInfo = CaffeineUtil.getOssDiameterPrefixCache(ossPath);
        String username = Paths.get(ossPath).subpath(0, 1).toString();
        String rootName = bucketInfo.getFolderName();
        FileIntroVO fileIntroVO = new FileIntroVO();
        String fileName = getName();
        fileIntroVO.setAgoTime(System.currentTimeMillis() - lastModified.getTime());
        fileIntroVO.setId(username + MyWebdavServlet.PATH_DELIMITER + rootName + MyWebdavServlet.PATH_DELIMITER + key);
        fileIntroVO.setIsFavorite(false);
        fileIntroVO.setIsFolder(isFolder());
        fileIntroVO.setName(fileName);
        Path keyPath = Paths.get(key);
        if (keyPath.getNameCount() > 1) {
            fileIntroVO.setPath(MyWebdavServlet.PATH_DELIMITER + Paths.get(rootName, key).getParent().toString() + MyWebdavServlet.PATH_DELIMITER);
        } else {
            fileIntroVO.setPath(MyWebdavServlet.PATH_DELIMITER + rootName + MyWebdavServlet.PATH_DELIMITER);
        }
        fileIntroVO.setSize(size);
        LocalDateTime updateTime = LocalDateTimeUtil.of(lastModified);
        String suffix = FileUtil.extName(fileName);
        fileIntroVO.setSuffix(suffix);
        fileIntroVO.setMd5(eTag);
        fileIntroVO.setContentType(FileContentTypeUtils.getContentType(suffix));
        fileIntroVO.setUploadDate(updateTime);
        fileIntroVO.setUpdateDate(updateTime);
        return fileIntroVO;
    }
}
