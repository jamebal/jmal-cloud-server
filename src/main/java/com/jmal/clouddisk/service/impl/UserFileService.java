package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.util.FileNameUtils;
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
        List<String> usernames = userList.stream().map(ConsumerDO::getUsername).toList();
        // 删除用户文件夹
        for (String username : usernames) {
            Path userDir = Paths.get(fileProperties.getRootDir(), username);
            if (FileUtil.exist(userDir.toFile())) {
                FileUtil.del(userDir);
            }
        }
        fileDAO.deleteAllByIdInBatch(userIdList);
    }

    public String uploadConsumerImage(UploadApiParamDTO upload) throws CommonException {
        MultipartFile multipartFile = upload.getFile();
        String username = upload.getUsername();
        String userId = upload.getUserId();
        String fileName = FileNameUtils.validateAndSanitizeFilename(upload.getFilename());
        MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
        MimeType mimeType;
        try {
            mimeType = allTypes.forName(multipartFile.getContentType());
            fileName += mimeType.getExtension();
        } catch (MimeTypeException e) {
            log.error(e.getMessage(), e);
        }
        Path userImagePath = Paths.get(fileProperties.getUserImgDir());
        // userImagePaths 不存在则新建
        commonUserFileService.upsertFolder(userImagePath, username, userId);
        File newFile = null;
        try (InputStream inputStream = multipartFile.getInputStream()) {
            newFile = commonUserFileService.createFileWithConversion(fileName, username, userImagePath, inputStream);
            return commonUserFileService.createFile(username, newFile, userId, true);
        } catch (IOException e) {
            if (newFile != null && newFile.exists()) {
                FileUtil.del(newFile);
            }
            log.error("Failed to upload image: {}", fileName, e);
            throw new CommonException(ExceptionType.WARNING, "上传失败: " + e.getMessage());
        }
    }

    public void setPublic(String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return;
        }
        fileDAO.updateIsPublicById(fileId);
    }

}
