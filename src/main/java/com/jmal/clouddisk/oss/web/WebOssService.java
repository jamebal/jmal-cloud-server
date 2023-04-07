package com.jmal.clouddisk.oss.web;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.URLUtil;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class WebOssService {


    public static String getObjectName(Path prePath, String ossPath, boolean isFolder) {
        String name = "";
        int ossPathCount = Paths.get(ossPath).getNameCount();
        if (prePath.getNameCount() > ossPathCount) {
            name = prePath.subpath(ossPathCount, prePath.getNameCount()).toString();
            if (!name.endsWith("/") && isFolder) {
                name = name + "/";
            }
        }
        return URLUtil.decode(name);
    }

    public ResponseResult<Object> searchFileAndOpenOssFolder(Path prePth) {
        List<FileIntroVO> fileIntroVOList = getOssFileList(prePth);
        ResponseResult<Object> result = ResultUtil.genResult();
        result.setCount(fileIntroVOList.size());
        result.setData(fileIntroVOList);
        return result;
    }

    public static List<FileIntroVO> getOssFileList(Path prePth) {
        List<FileIntroVO> fileIntroVOList = new ArrayList<>();
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath == null) {
            return fileIntroVOList;
        }
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, true);
        List<FileInfo> list = ossService.getFileInfoList(objectName);
        if (!list.isEmpty()) {
            fileIntroVOList = list.stream().map(fileInfo -> fileInfo.toFileIntroVO(getOssRootFolderName(ossPath))).toList();
        }
        return fileIntroVOList;
    }

    private static String getOssRootFolderName(String ossPath) {
        return CaffeineUtil.getOssDiameterPrefixCache(ossPath).getFolderName();
    }

    public Optional<FileIntroVO> readToText(String ossPath, Path prePth) {
        IOssService ossService = OssConfigService.getOssStorageService(ossPath);
        String objectName = getObjectName(prePth, ossPath, false);
        try (AbstractOssObject abstractOssObject = ossService.getObject(objectName);
             InputStream inputStream = abstractOssObject.getInputStream()) {
            FileIntroVO fileIntroVO = new FileIntroVO();
            FileInfo fileInfo = abstractOssObject.getFileInfo();
            String context;
            if (fileInfo != null && inputStream != null) {
                fileIntroVO = fileInfo.toFileIntroVO(getOssRootFolderName(ossPath));
                context = IoUtil.read(inputStream, StandardCharsets.UTF_8);
                fileIntroVO.setContentText(context);
            }
            return Optional.of(fileIntroVO);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }
}
