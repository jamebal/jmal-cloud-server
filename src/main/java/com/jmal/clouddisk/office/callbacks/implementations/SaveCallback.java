package com.jmal.clouddisk.office.callbacks.implementations;

import cn.hutool.http.HttpUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.dto.FileBaseOperationPermissionDTO;
import com.jmal.clouddisk.office.callbacks.Callback;
import com.jmal.clouddisk.office.callbacks.Status;
import com.jmal.clouddisk.office.model.Track;
import com.jmal.clouddisk.oss.web.WebOssCommonService;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.FileVersionEvent;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.service.impl.MessageService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jmal
 * @Description 在执行保存请求时处理回调
 * @date 2022/8/11 16:29
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SaveCallback implements Callback {

    private final FileProperties fileProperties;

    private final UserLoginHolder userLoginHolder;

    private final IFileDAO fileDAO;

    private final CommonFileService commonFileService;

    private final MessageService messageService;

    private final LogService logService;

    private final IUserService userService;

    private final WebOssService webOssService;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public int handle(Track body) {
        int result = 0;
        try {

            String fileUsername;
            String userId;
            FileBaseOperationPermissionDTO fileBaseOperationPermissionDTO = fileDAO.findFileBaseOperationPermissionDTOById(body.getFileId());
            if (fileBaseOperationPermissionDTO == null) {
                Path prePath = Paths.get(body.getFileId());
                String ossPath = CaffeineUtil.getOssPath(prePath);
                if (ossPath == null) {
                    return -1;
                }
                fileUsername = WebOssCommonService.getUsernameByOssPath(ossPath);
                userId = userService.getUserIdByUserName(fileUsername);

                fileBaseOperationPermissionDTO = new FileBaseOperationPermissionDTO();
                String objectName = WebOssService.getObjectName(prePath, ossPath, false);
                String ossRootFolderName = WebOssCommonService.getOssRootFolderName(ossPath);
                fileBaseOperationPermissionDTO.setPath(WebOssCommonService.getPathByObjectName(ossRootFolderName, objectName));
                fileBaseOperationPermissionDTO.setName(WebOssService.getFilenameFromObjectName(objectName));

            } else {
                // 检查权限
                userId = fileBaseOperationPermissionDTO.getUserId();
                fileUsername = userService.getUserNameById(userId);
                List<OperationPermission> operationPermissionList = fileBaseOperationPermissionDTO.getOperationPermissionList();
                commonFileService.checkPermissionUserId(userId, operationPermissionList, OperationPermission.PUT);
            }

            // 下载最新文件
            Path path = Paths.get(fileProperties.getRootDir(), fileUsername, fileBaseOperationPermissionDTO.getPath(), fileBaseOperationPermissionDTO.getName());
            long size = HttpUtil.downloadFile(body.getUrl(), path.toString());

            // 判断是否为oss存储
            Path prePath = Paths.get(fileUsername, fileBaseOperationPermissionDTO.getPath(), fileBaseOperationPermissionDTO.getName());
            String ossPath = CaffeineUtil.getOssPath(prePath);
            if (ossPath != null) {
                // 保存到oss
                webOssService.putOfficeCallback(ossPath, prePath, path.toFile(), size);
            } else {
                String md5 = size + "/" + fileBaseOperationPermissionDTO.getName();
                LocalDateTime updateDate = LocalDateTime.now(TimeUntils.ZONE_ID);
                // 推送修改文件的通知
                FileDocument fileDocument = fileBaseOperationPermissionDTO.toFileDocument();
                fileDocument.setSize(size);
                fileDocument.setMd5(md5);
                fileDocument.setUpdateDate(updateDate);
                // 修改文件日志
                logService.asyncAddLogFileOperation(fileUsername, Paths.get(fileBaseOperationPermissionDTO.getPath(), fileBaseOperationPermissionDTO.getName()).toString(), "修改文件");
                messageService.pushMessage(userLoginHolder.getUsername(), fileDocument, Constants.UPDATE_FILE);

                // 修改文件之后保存历史版本
                String relativePath = Paths.get(fileBaseOperationPermissionDTO.getPath(), fileBaseOperationPermissionDTO.getName()).toString();
                eventPublisher.publishEvent(new FileVersionEvent(this, fileUsername, relativePath, userId, userLoginHolder.getUsername()));
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return 1;
        }
        return result;
    }

    @Override
    public int getStatus() {
        // 2 - 文件已准备好保存
        return Status.SAVE.getCode();
    }
}
