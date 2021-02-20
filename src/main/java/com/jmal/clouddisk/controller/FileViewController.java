package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author jmal
 * @Description 文件资源管理
 * @Date 2020/10/19 3:55 下午
 */
@Api(tags = "文件资源管理")
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@Controller
public class FileViewController {

    @Autowired
    private IFileService fileService;

    @Autowired
    IShareService shareService;

    @ApiOperation("预览文档里的图片")
    @GetMapping("/public/view")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public String imageRelativePath(@RequestParam String relativePath,@RequestParam String userId) {
        ResultUtil.checkParamIsNull(relativePath,userId);
        return fileService.publicViewFile(relativePath, userId);
    }

    @ApiOperation("分享：预览文件")
    @GetMapping("/public/s/preview/{fileId}/{shareId}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public String publicPreview(@PathVariable String fileId, @PathVariable String shareId) {
        ShareDO shareDO = shareService.getShare(shareId);
        boolean whetherExpired = shareService.checkWhetherExpired(shareDO);
        if(whetherExpired){
            return fileService.viewFile(shareDO.getFileId(), fileId, "preview");
        }
        return "forward:/public/s/invalid";
    }

    @ApiOperation("分享：下载单个文件")
    @GetMapping("/public/s/download/{fileId}/{shareId}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public String publicDownload(@PathVariable String fileId, @PathVariable String shareId) {
        ShareDO shareDO = shareService.getShare(shareId);
        boolean whetherExpired = shareService.checkWhetherExpired(shareDO);
        if(whetherExpired){
            return fileService.viewFile(shareDO.getFileId(), fileId, "download");
        }
        return "forward:/public/s/invalid";
    }

}
