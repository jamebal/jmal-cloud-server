package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.UploadApiParam;
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
 * @blame jmal
 */
@Controller
@Api(tags = "markdown管理")
@RestController
public class MarkDownController {

    @Autowired
    private IFileService fileService;

    @Autowired
    IUserService service;

    /***
     * 获取markdown内容
     * @param mark
     * @return
     * @throws CommonException
     */
    @ApiOperation("获取markdown内容")
    @GetMapping("/public/p")
    @ResponseBody
    public ResponseResult<Object> getMarkDownContent(String mark) throws CommonException {
        return fileService.getMarkDownContent(mark);
    }

    /***
     * 新建文档
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("新建文档")
    @PostMapping("/markdown/add")
    @ResponseBody
    public ResponseResult<Object> newMarkdown(@RequestBody UploadApiParam upload) throws CommonException {
        ResultUtil.checkParamIsNull(upload.getUserId(),upload.getUsername(),upload.getFilename(),upload.getContentText());
        return fileService.newMarkdown(upload);
    }

    /***
     * 编辑文档(根据fileId)
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("编辑文档(根据fileId)")
    @PostMapping("/markdown/edit")
    @ResponseBody
    public ResponseResult<Object> editMarkdown(@RequestBody UploadApiParam upload) throws CommonException {
        ResultUtil.checkParamIsNull(upload.getFileId(),upload.getUserId(),upload.getUsername(),upload.getFilename(),upload.getContentText());
        return fileService.editMarkdown(upload);
    }

    /***
     * 编辑文档(根据path)
     * @param upload
     * @return
     * @throws CommonException
     */
    @ApiOperation("编辑文档(根据path)")
    @PostMapping("/markdown/edit1")
    @ResponseBody
    public ResponseResult<Object> editMarkdownByPath(@RequestBody UploadApiParam upload) throws CommonException {
        ResultUtil.checkParamIsNull(upload.getUsername(),upload.getRelativePath(),upload.getContentText());
        return fileService.editMarkdownByPath(upload);
    }

    /***
     * 上传文档里的图片
     * @param upload
     * @return
     * @throws IOException
     */
    @ApiOperation("上传文档里的图片")
    @PostMapping("/upload-markdown-image")
    @ResponseBody
    public ResponseResult<Object> uploadMarkdownImage(UploadApiParam upload) throws CommonException {
        System.out.println("upload-markdown-image:" + upload.toString());
        return fileService.uploadMarkdownImage(upload);
    }

    /**
     * 预览文档里的图片
     * @param fileId fileId
     * @return
     */
    @ApiOperation("预览文档里的图片")
    @GetMapping("/public/image/{fileId}")
    public void imagePreview(HttpServletRequest request, HttpServletResponse response, @PathVariable String fileId) throws CommonException {
        ResultUtil.checkParamIsNull(fileId);
        List<String> list = new ArrayList<>();
        list.add(fileId);
        fileService.publicNginx(request, response, list, false);
    }

    /**
     * 预览文档里的图片
     * @param relativePath relativePath
     * @return
     */
    @ApiOperation("预览文档里的图片")
    @GetMapping("/public/view")
    public void imageRelativePath(HttpServletRequest request, HttpServletResponse response, String relativePath,String userId) throws CommonException {
        ResultUtil.checkParamIsNull(relativePath,userId);
        fileService.publicNginx(request, response, relativePath, userId);
    }
}
