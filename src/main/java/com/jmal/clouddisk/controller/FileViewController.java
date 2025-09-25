package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author jmal
 * @Description 文件资源管理
 * @Date 2020/10/19 3:55 下午
 */
@Tag(name = "文件资源管理")
@Controller
public class FileViewController {

    @Autowired
    private IFileService fileService;

    @Autowired
    IShareService shareService;

    @Operation(summary = "预览文档里的图片")
    @GetMapping("/api/public/view")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public String imageRelativePath(@RequestParam String relativePath,@RequestParam String userId) {
        ResultUtil.checkParamIsNull(relativePath,userId);
        return fileService.publicViewFile(relativePath, userId);
    }

    @Operation(summary = "分享：预览文件")
    @GetMapping("/api/public/s/preview/{filename}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public String publicPreview(@RequestParam String fileId, @RequestParam String shareId, @RequestParam String shareToken) {
        shareService.validShare(shareToken, shareId);
        return fileService.viewFile(fileId, fileId, shareToken, "preview");
    }

    @Operation(summary = "分享：下载单个文件")
    @GetMapping("/api/public/s/download/{filename}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public String publicDownload(@RequestParam String fileId, @RequestParam String shareId, @RequestParam String shareToken) {
        shareService.validShare(shareToken, shareId);
        return fileService.viewFile(fileId, fileId, shareToken, "download");
    }

}
