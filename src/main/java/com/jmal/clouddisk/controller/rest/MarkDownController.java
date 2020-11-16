package com.jmal.clouddisk.controller.rest;

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
import org.springframework.web.bind.annotation.*;

/**
 * @Description markdown
 * @author jmal
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
    public ResponseResult<Object> getMarkDownContent(String mark, Integer pageIndex, Integer pageSize) {
        int skip = 0, limit = 5;
        if(pageIndex != null && pageSize != null){
            skip = (pageIndex - 1) * pageSize;
            limit = pageSize;
        }
        return fileService.getMarkDownContent(mark, skip, limit);
    }

    @ApiOperation("编辑文档(根据fileId)")
    @PostMapping("/markdown/edit")
    public ResponseResult<Object> editMarkdown(@RequestBody UploadApiParamDTO upload) {
        return fileService.editMarkdown(upload);
    }

    @ApiOperation("编辑文档(根据path)")
    @PostMapping("/markdown/edit1")
    public ResponseResult<Object> editMarkdownByPath(@RequestBody UploadApiParamDTO upload) {
        ResultUtil.checkParamIsNull(upload.getUsername(),upload.getUserId(),upload.getRelativePath(),upload.getContentText());
        return fileService.editMarkdownByPath(upload);
    }

    @ApiOperation("上传文档里的图片")
    @PostMapping("/upload-markdown-image")
    public ResponseResult<Object> uploadMarkdownImage(UploadImageDTO uploadImageDTO) {
        return fileService.uploadMarkdownImage(uploadImageDTO);
    }

}
