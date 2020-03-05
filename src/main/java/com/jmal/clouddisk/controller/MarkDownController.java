package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.common.exception.CommonException;
import com.jmal.clouddisk.service.IUploadFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * @Description UserController
 * @blame jmal
 */
@Controller
@RequestMapping("/public")
@Api(tags = "markdown")
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
    @GetMapping("/p")
    @ResponseBody
    public ResponseResult<Object> getMarkDownContent(String mark) throws CommonException {
        return fileService.getMarkDownContent(mark);
    }
}
