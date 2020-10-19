package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.service.IFileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


/**
 * @author jmal
 * @Description TODO
 * @Date 2020/10/19 3:55 下午
 */
@Api(tags = "文件管理")
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@Controller
public class FileViewController {

    @Autowired
    private IFileService fileService;

    @ApiOperation("下载单个文件")
    @GetMapping("/file/download/{fileIds}")
    public String downLoad(@PathVariable String fileIds) {
        return fileService.viewFile(fileIds);
    }

    @ApiOperation("预览文件")
    @GetMapping("/file/preview/{fileIds}")
    public String preview(@PathVariable String fileIds) {
        return fileService.viewFile(fileIds);
    }
}
