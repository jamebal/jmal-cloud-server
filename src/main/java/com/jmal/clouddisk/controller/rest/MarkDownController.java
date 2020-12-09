package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.model.ArticleDTO;
import com.jmal.clouddisk.model.ArticleParamDTO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.UploadImageDTO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author jmal
 * @Description markdown
 */
@Controller
@Api(tags = "markdown管理")
@RestController
public class MarkDownController {

    @Autowired
    private IFileService fileService;

    @Autowired
    IUserService service;

    @ApiOperation("获取markdown内容")
    @GetMapping("/public/p")
    public ResponseResult<Object> getMarkDownContent(ArticleDTO articleDTO, Integer pageIndex, Integer pageSize) {
        articleDTO.setPageIndex(pageIndex);
        articleDTO.setPageSize(pageSize);
        return fileService.getMarkDownContent(articleDTO);
    }

    @ApiOperation("编辑文档(根据fileId)")
    @PostMapping("/markdown/edit")
    public ResponseResult<Object> editMarkdown(@RequestBody @Validated ArticleParamDTO upload) {
        return fileService.editMarkdown(upload);
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
    public ResponseResult<Object> uploadMarkdownImage(UploadImageDTO uploadImageDTO) {
        return fileService.uploadMarkdownImage(uploadImageDTO);
    }

}
