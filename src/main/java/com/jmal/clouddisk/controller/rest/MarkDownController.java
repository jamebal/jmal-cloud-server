package com.jmal.clouddisk.controller.rest;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IMarkdownService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author jmal
 * @Description markdown
 */
@Controller
@Tag(name = "markdown管理")
@RestController
public class MarkDownController {

    @Autowired
    private IMarkdownService markdownService;

    @Autowired
    private WebOssService webOssService;

    @Autowired
    private IFileService fileService;

    @Autowired
    private IUserService userService;

    @Operation(summary = "获取markdown内容")
    @GetMapping("/markdown/p")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<?> getMarkDownContent(ArticleDTO articleDTO, Integer pageIndex, Integer pageSize) {
        articleDTO.setPageIndex(pageIndex);
        articleDTO.setPageSize(pageSize);
        if (CharSequenceUtil.isBlank(articleDTO.getMark())) {
            return markdownService.getMarkdownList(articleDTO);
        } else {
            return markdownService.getMarkDownOne(articleDTO);
        }
    }

    @Operation(summary = "编辑文档(根据fileId)")
    @PostMapping("/markdown/edit")
    @Permission("cloud:file:update")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    public ResponseResult<Object> editMarkdown(@RequestBody @Validated ArticleParamDTO upload) {
        return markdownService.editMarkdown(upload);
    }

    @Operation(summary = "修改文档排序")
    @PostMapping("/markdown/sort")
    @Permission("cloud:file:update")
    @LogOperatingFun
    public ResponseResult<Object> sortMarkdown(@RequestBody String[] fileIdList) {
        return markdownService.sortMarkdown(Arrays.asList(fileIdList));
    }

    @Operation(summary = "删除草稿")
    @DeleteMapping("/markdown/deleteDraft")
    @Permission("cloud:file:delete")
    @LogOperatingFun(logType = LogOperation.Type.OPERATION)
    public ResponseResult<Object> deleteDraft(@RequestParam String fileId, @RequestParam String username) {
        return markdownService.deleteDraft(fileId, username);
    }

    @Operation(summary = "编辑文档(根据path)")
    @PostMapping("/markdown/edit1")
    @Permission("cloud:file:update")
    public ResponseResult<Object> editTextByPath(@RequestBody UploadApiParamDTO upload) {
        ResultUtil.checkParamIsNull(upload.getUsername(), upload.getUserId(), upload.getRelativePath(), upload.getContentText());
        if (!CharSequenceUtil.isBlank(upload.getMountFileId())) {
            FileDocument fileDocument = fileService.getById(upload.getMountFileId());
            upload.setUserId(fileDocument.getUserId());
            upload.setUsername(userService.getUserNameById(fileDocument.getUserId()));
            upload.setRelativePath(fileDocument.getPath() + fileDocument.getName());
            upload.setOperationPermissionList(fileDocument.getOperationPermissionList());
        }
        Path prePth = Paths.get(upload.getUsername(), upload.getRelativePath());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            webOssService.putObjectText(ossPath, prePth, upload.getContentText());
            return ResultUtil.success();
        }
        return markdownService.editTextByPath(upload);
    }

    @Operation(summary = "上传文档里的图片")
    @PostMapping("/upload-markdown-image")
    @Permission("cloud:file:upload")
    public ResponseResult<Object> uploadMarkdownImage(UploadImageDTO upload) {
        if(CharSequenceUtil.isBlank(upload.getUserId()) || CharSequenceUtil.isBlank(upload.getUsername())) {
            return ResultUtil.warning("参数里缺少 userId 或 username");
        }
        return markdownService.uploadMarkdownImage(upload);
    }

    @Operation(summary = "上传文档里链接图片")
    @PostMapping("/upload-markdown-link-image")
    @Permission("cloud:file:upload")
    @LogOperatingFun
    public ResponseResult<Object> uploadMarkdownLinkImage(HttpServletRequest request, @RequestBody UploadImageDTO uploadImageDTO) {
        String userId = uploadImageDTO.getUserId();
        String username = uploadImageDTO.getUsername();
        if(CharSequenceUtil.isBlank(userId)){
            userId = request.getHeader("userId");
            uploadImageDTO.setUserId(userId);
        }
        if(CharSequenceUtil.isBlank(username)){
            username = request.getHeader("username");
            uploadImageDTO.setUsername(username);
        }
        if(CharSequenceUtil.isBlank(userId) || CharSequenceUtil.isBlank(username)) {
            return ResultUtil.warning("请求头里或参数里必须含有userId和username");
        }
        return markdownService.uploadMarkdownLinkImage(uploadImageDTO);
    }

}
