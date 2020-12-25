package com.jmal.clouddisk.controller.rest;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.service.IMarkdownService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.FileServiceImpl;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author jmal
 * @Description markdown
 */
@Controller
@Api(tags = "markdown管理")
@RestController
public class MarkDownController {

    @Autowired
    private IMarkdownService fileService;

    @Autowired
    IUserService service;

    @ApiOperation("获取markdown内容")
    @GetMapping("/public/p")
    public ResponseResult<? extends Object> getMarkDownContent(ArticleDTO articleDTO, Integer pageIndex, Integer pageSize) {
        articleDTO.setPageIndex(pageIndex);
        articleDTO.setPageSize(pageSize);
        if (StringUtils.isEmpty(articleDTO.getMark())) {
            return fileService.getMarkdownList(articleDTO);
        } else {
            return fileService.getMarkDownOne(articleDTO);
        }
    }

    @ApiOperation("编辑文档(根据fileId)")
    @PostMapping("/markdown/edit")
    public ResponseResult<Object> editMarkdown(@RequestBody @Validated ArticleParamDTO upload) {
        return fileService.editMarkdown(upload);
    }

    @ApiOperation("修改文档排序")
    @PostMapping("/markdown/sort")
    public ResponseResult<Object> sortMarkdown(@RequestBody String[] fileIdList) {
        return fileService.sortMarkdown(Arrays.asList(fileIdList));
    }

    @ApiOperation("删除草稿")
    @DeleteMapping("/markdown/deleteDraft")
    public ResponseResult<Object> deleteDraft(@RequestParam String fileId, @RequestParam String username) {
        return fileService.deleteDraft(fileId, username);
    }

    @ApiOperation("编辑文档(根据path)")
    @PostMapping("/markdown/edit1")
    public ResponseResult<Object> editMarkdownByPath(@RequestBody UploadApiParamDTO upload) {
        ResultUtil.checkParamIsNull(upload.getUsername(), upload.getUserId(), upload.getRelativePath(), upload.getContentText());
        return fileService.editMarkdownByPath(upload);
    }

    @ApiOperation("上传文档里的图片")
    @PostMapping("/upload-markdown-image")
    public ResponseResult<Object> uploadMarkdownImage(UploadImageDTO upload) {
        if(StringUtils.isEmpty(upload.getUserId()) || StringUtils.isEmpty(upload.getUsername())) {
            return ResultUtil.warning("参数里缺少 userId 或 username");
        }
        return fileService.uploadMarkdownImage(upload);
    }

    @ApiOperation("上传文档里链接图片")
    @PostMapping("/upload-markdown-link-image")
    public ResponseResult<Object> uploadMarkdownLinkImage(HttpServletRequest request, @RequestBody UploadImageDTO uploadImageDTO) {
        String userId = uploadImageDTO.getUserId();
        String username = uploadImageDTO.getUsername();
        if(StringUtils.isEmpty(userId)){
            userId = request.getHeader("userId");
            uploadImageDTO.setUserId(userId);
        }
        if(StringUtils.isEmpty(userId)){
            username = request.getHeader("username");
            uploadImageDTO.setUsername(username);
        }
        if(StringUtils.isEmpty(userId) || StringUtils.isEmpty(username)) {
            return ResultUtil.warning("请求头里或参数里必须含有userId和username");
        }
        return fileService.uploadMarkdownLinkImage(uploadImageDTO);
    }

}
