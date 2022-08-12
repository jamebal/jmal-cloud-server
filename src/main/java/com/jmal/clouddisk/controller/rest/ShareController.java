package com.jmal.clouddisk.controller.rest;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.FileInterceptor;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @Description share
 * @Author jmal
 * @Date 2020-03-17 16:22
 */
@Tag(name = "文件分享")
@RestController
@Slf4j
public class ShareController {

    @Autowired
    IShareService shareService;

    @Autowired
    IFileService fileService;

    @Autowired
    IUserService userService;

    @Autowired
    FileInterceptor fileInterceptor;

    @Operation(summary = "该分享已失效")
    @GetMapping("/public/s/invalid")
    public String invalid() {
        return "该分享已失效";
    }

    @Operation(summary = "生成分享链接")
    @PostMapping("/share/generate")
    @Permission("cloud:file:update")
    @LogOperatingFun
    public ResponseResult<Object> generateLink(@RequestBody ShareDO share) {
        ResultUtil.checkParamIsNull(share.getFileId(), share.getUserId());
        return shareService.generateLink(share);
    }

    @Operation(summary = "取消分享")
    @DeleteMapping("/share/cancel")
    @Permission("cloud:file:delete")
    @LogOperatingFun
    public ResponseResult<Object> cancelShare(@RequestParam String[] shareId) {
        return shareService.cancelShare(shareId);
    }

    @Operation(summary = "分享列表")
    @GetMapping("/share/list")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> shareList(UploadApiParamDTO upload) {
        return shareService.shareList(upload);
    }

    @Operation(summary = "访问分享链接")
    @GetMapping("/public/access-share")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> accessShare(@RequestParam String share, Integer pageIndex, Integer pageSize) {
        ResultUtil.checkParamIsNull(share);
        return shareService.accessShare(share, pageIndex, pageSize);
    }

    @Operation(summary = "获取分享者信息")
    @GetMapping("/public/get/sharer")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<SharerDTO> getSharer(@RequestParam String userId) {
        return shareService.getSharer(userId);
    }

    @Operation(summary = "访问分享链接里的目录")
    @GetMapping("public/access-share/open")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> accessShareOpenDir(@RequestParam String share, @RequestParam String fileId, Integer pageIndex, Integer pageSize) {
        ShareDO shareDO = shareService.getShare(share);
        if(!shareService.checkWhetherExpired(shareDO)){
            return ResultUtil.warning("该分享已过期");
        }
        return shareService.accessShareOpenDir(shareDO, fileId, pageIndex, pageSize );
    }

    @Operation(summary = "打包下载")
    @GetMapping("/public/s/packageDownload")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public void publicPackageDownload(HttpServletRequest request, HttpServletResponse response, @RequestParam String shareId, @RequestParam String[] fileIds) {
        boolean whetherExpired = shareService.checkWhetherExpired(shareId);
        if(whetherExpired){
            if (fileIds != null && fileIds.length > 0) {
                List<String> fileIdList = Arrays.asList(fileIds);
                fileService.publicPackageDownload(request, response, fileIdList);
            } else {
                throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
            }
        } else {
            try (OutputStream out = response.getOutputStream()) {
                out.write(invalid().getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    @Operation(summary = "显示缩略图")
    @GetMapping("/articles/s/view/thumbnail")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> articlesThumbnail(String id) {
        return thumbnail(id);
    }

    @Operation(summary = "显示缩略图")
    @GetMapping("/public/s/view/thumbnail")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> publicThumbnail(String id) {
        return thumbnail(id);
    }

    private ResponseEntity<Object> thumbnail(String id) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.thumbnail(id, null);
        if (fileInterceptor.isNotAllowAccess(file.orElse(null))) {
            return null;
        }
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileDocument.getName())
                        .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
                        .header(HttpHeaders.CONNECTION, "close")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileDocument.getContent().length))
                        .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                        .body(fileDocument.getContent())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    @Operation(summary = "读取simText文件")
    @GetMapping("/public/s/preview/text")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> preview(@RequestParam String shareId,@RequestParam String fileId) {
        ShareDO shareDO = shareService.getShare(shareId);
        if(!shareService.checkWhetherExpired(shareDO)){
            return ResultUtil.warning("该分享已过期");
        }
        ConsumerDO consumer = userService.userInfoById(shareDO.getUserId());
        if(consumer == null){
            return ResultUtil.warning("该分享已过期");
        }
        return ResultUtil.success(fileService.getById(fileId,consumer.getUsername()));
    }

    @Operation(summary = "根据id获取分享的文件信息")
    @GetMapping("/public/file_info/{fileId}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<FileDocument> getFileById(@PathVariable String fileId) {
        FileDocument fileDocument = fileService.getById(fileId);
        if (fileDocument == null) {
            return ResultUtil.error(ExceptionType.FILE_NOT_FIND.getMsg());
        }
        if (BooleanUtil.isTrue(fileDocument.getIsShare()) && (System.currentTimeMillis() < Convert.toLong(fileDocument.getExpiresAt(),   0L))) {
            return ResultUtil.success(fileService.getById(fileId));
        }
        return ResultUtil.warning("该分享已失效");
    }
}
