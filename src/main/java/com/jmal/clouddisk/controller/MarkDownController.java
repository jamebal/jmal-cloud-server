package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

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
    @ResponseBody
    public ResponseResult<Object> getMarkDownContent(String mark) {
        return fileService.getMarkDownContent(mark);
    }

    @ApiOperation("新建文档")
    @PostMapping("/markdown/add")
    @ResponseBody
    public ResponseResult<Object> newMarkdown(@RequestBody UploadApiParamDTO upload) {
        ResultUtil.checkParamIsNull(upload.getUserId(),upload.getUsername(),upload.getFilename(),upload.getContentText());
        return fileService.newMarkdown(upload);
    }

    @ApiOperation("编辑文档(根据fileId)")
    @PostMapping("/markdown/edit")
    @ResponseBody
    public ResponseResult<Object> editMarkdown(@RequestBody UploadApiParamDTO upload) {
        ResultUtil.checkParamIsNull(upload.getFileId(),upload.getUserId(),upload.getUsername(),upload.getFilename(),upload.getContentText());
        return fileService.editMarkdown(upload);
    }

    @ApiOperation("编辑文档(根据path)")
    @PostMapping("/markdown/edit1")
    @ResponseBody
    public ResponseResult<Object> editMarkdownByPath(@RequestBody UploadApiParamDTO upload) {
        ResultUtil.checkParamIsNull(upload.getUsername(),upload.getUserId(),upload.getRelativePath(),upload.getContentText());
        return fileService.editMarkdownByPath(upload);
    }

    @ApiOperation("上传文档里的图片")
    @PostMapping("/upload-markdown-image")
    @ResponseBody
    public ResponseResult<Object> uploadMarkdownImage(UploadApiParamDTO upload) {
        return fileService.uploadMarkdownImage(upload);
    }

    @ApiOperation("预览文档里的图片")
    @GetMapping("/public/image/{fileId}")
    public void imagePreview(HttpServletRequest request, HttpServletResponse response, @PathVariable String fileId) {
        ResultUtil.checkParamIsNull(fileId);
        List<String> list = new ArrayList<>();
        list.add(fileId);
        fileService.publicNginx(request, response, list, false);
    }

    @ApiOperation("预览文档里的图片")
    @GetMapping("/public/view")
    public void imageRelativePath(HttpServletRequest request, HttpServletResponse response, String relativePath,String userId) {
        ResultUtil.checkParamIsNull(relativePath,userId);
        fileService.publicNginx(request, response, relativePath, userId);
    }
}
