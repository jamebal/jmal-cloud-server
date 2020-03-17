package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.common.exception.CommonException;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.service.IUploadFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * @Description markdown
 * @blame jmal
 */
@Controller
@Api(tags = "markdown")
@RestController
public class MarkDownController {

    @Autowired
    private IUploadFileService fileService;

    @Autowired
    IUserService service;

    @Value("${root-path}")
    String rootPath;

    /***
     * 获取markdown内容
     * @param mark
     * @return
     * @throws CommonException
     */
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
    @PostMapping("/markdown/add")
    @ResponseBody
    public ResponseResult<Object> newMarkdown(@RequestBody UploadApiParam upload) throws CommonException {
        ResultUtil.checkParamIsNull(upload.getUserId(),upload.getUsername(),upload.getFilename(),upload.getContentText());
        return fileService.newMarkdown(upload);
    }

    /***
     * 编辑文档
     * @param upload
     * @return
     * @throws CommonException
     */
    @PostMapping("/markdown/edit")
    @ResponseBody
    public ResponseResult<Object> editMarkdown(@RequestBody UploadApiParam upload) throws CommonException {
        ResultUtil.checkParamIsNull(upload.getFileId(),upload.getUserId(),upload.getUsername(),upload.getFilename(),upload.getContentText());
        return fileService.editMarkdown(upload);
    }
}
