package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserFileService {

    private final FileProperties fileProperties;

    private final IFileDAO fileDAO;

    private final CommonUserFileService commonUserFileService;

    public void deleteAllByUser(List<ConsumerDO> userList) {
        if (userList == null || userList.isEmpty()) {
            return;
        }
        List<String> userIdList = userList.stream().map(ConsumerDO::getId).toList();
        fileDAO.deleteAllByIdInBatch(userIdList);
    }

    public String uploadConsumerImage(UploadApiParamDTO upload) throws CommonException {
        MultipartFile multipartFile = upload.getFile();
        String username = upload.getUsername();
        String userId = upload.getUserId();
        String fileName = upload.getFilename();
        MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
        MimeType mimeType;
        try {
            mimeType = allTypes.forName(multipartFile.getContentType());
            fileName += mimeType.getExtension();
        } catch (MimeTypeException e) {
            log.error(e.getMessage(), e);
        }
        Path userImagePaths = Paths.get(fileProperties.getUserImgDir());
        // userImagePaths 不存在则新建
        commonUserFileService.upsertFolder(userImagePaths, username, userId);
        File newFile;
        try (InputStream inputStream = multipartFile.getInputStream()) {
            if (commonUserFileService.getDisabledWebp(userId) || ("ico".equals(FileUtil.getSuffix(fileName)))) {
                newFile = Paths.get(fileProperties.getRootDir(), username, userImagePaths.toString(), fileName).toFile();
                FileUtil.writeFromStream(inputStream, newFile);
            } else {
                fileName = fileName + Constants.POINT_SUFFIX_WEBP;
                newFile = Paths.get(fileProperties.getRootDir(), username, userImagePaths.toString(), fileName).toFile();
                ImageMagickProcessor.convertToWebpFile(inputStream, newFile);
            }
        } catch (IOException e) {
            throw new CommonException(2, "上传失败");
        }
        return commonUserFileService.createFile(username, newFile, userId, true);
    }

    public void setPublic(String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return;
        }
        fileDAO.updateIsPublicById(fileId);
    }

}
