package com.jmal.clouddisk.controller.rest;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Optional;


@Tag(name = "分享链接里的上传接口")
@RestController
@RequiredArgsConstructor
public class ShareUploadFileController {

    private final IFileService fileService;

    private final IShareService shareService;

    private final IUserService userService;


    @Operation(summary = "检查文件是否存在")
    @PostMapping("/public/checkExist")
    public ResponseResult<Object> publicCheckFileExist(HttpServletRequest request, @RequestBody UploadApiParamDTO upload) throws IOException {
        setUploadParameter(request, upload);
        return fileService.checkChunkUploaded(upload);
    }

    @Operation(summary = "检查文件/分片是否存在")
    @GetMapping("/public/upload")
    public ResponseResult<Object> checkUpload(HttpServletRequest request, UploadApiParamDTO upload) throws IOException {
        setUploadParameter(request, upload);
        return fileService.checkChunkUploaded(upload);
    }

    @Operation(summary = "文件上传")
    @PostMapping("/public/upload")
    public ResponseResult<Object> uploadPost(HttpServletRequest request, UploadApiParamDTO upload) throws IOException {
        setUploadParameter(request, upload);
        return fileService.upload(upload);
    }

    @Operation(summary = "文件夹上传")
    @PostMapping("/public/upload-folder")
    public ResponseResult<Object> uploadFolder(HttpServletRequest request, UploadApiParamDTO upload) {
        setUploadParameter(request, upload);
        return fileService.uploadFolder(upload);
    }

    @Operation(summary = "合并文件")
    @PostMapping("/public/merge")
    public ResponseResult<Object> merge(HttpServletRequest request, UploadApiParamDTO upload) throws IOException {
        setUploadParameter(request, upload);
        return fileService.merge(upload);
    }

    private void setUploadParameter(HttpServletRequest request, UploadApiParamDTO upload) {
        if (request.getHeader(Constants.SHARE_ID) == null || request.getHeader(Constants.SHARE_TOKEN) == null) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS);
        }
        ShareDO shareDO = shareService.getShare(request.getHeader(Constants.SHARE_ID));
        shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        String fileId = upload.getFileId();
        if (CharSequenceUtil.isBlank(fileId) || CharSequenceUtil.equals(fileId, "null")) {
            fileId = shareDO.getFileId();
        }
        Optional<FileDocument> optionalFileDocument = fileService.getById(fileId, false);

        if (optionalFileDocument.isEmpty()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }

        FileDocument fileDocument = optionalFileDocument.get();

        if (!fileDocument.getIsFolder()) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "分享链接不是文件夹，无法上传文件");
        }

        String currentDirectory = fileDocument.getPath() + fileDocument.getName() + "/";
        String userId = fileDocument.getUserId();
        String username = userService.getUserNameById(userId);
        upload.setCurrentDirectory(currentDirectory);
        upload.setUsername(username);
        upload.setUserId(userId);
        upload.setOperationPermissionList(fileDocument.getOperationPermissionList());
    }

}
