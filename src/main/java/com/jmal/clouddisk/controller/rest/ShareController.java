package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.FileInterceptor;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    public static final String SHARE_EXPIRED = "该分享已过期";

    final
    IShareService shareService;

    final
    IFileService fileService;

    final
    IUserService userService;

    final
    FileInterceptor fileInterceptor;

    public ShareController(IShareService shareService, IFileService fileService, IUserService userService, FileInterceptor fileInterceptor) {
        this.shareService = shareService;
        this.fileService = fileService;
        this.userService = userService;
        this.fileInterceptor = fileInterceptor;
    }

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

    @Operation(summary = "获取分享信息")
    @GetMapping("/get/share")
    @Permission("cloud:file:list")
    @LogOperatingFun
    public ResponseResult<ShareDO> getShare(@RequestParam String shareId) {
        return ResultUtil.success(shareService.getShare(shareId));
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

    @Operation(summary = "验证提取码")
    @PostMapping("/public/valid-share-code")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> validShareCode(@RequestBody Map<String, String> body) {
        String shareId = body.get(Constants.SHARE_ID);
        String shareCode = body.get("shareCode");
        ResultUtil.checkParamIsNull(shareId, shareCode);
        return shareService.validShareCode(shareId, shareCode);
    }

    @Operation(summary = "访问分享链接")
    @GetMapping("/public/access-share")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> accessShare(HttpServletRequest request, @RequestParam String share, Integer pageIndex, Integer pageSize) {
        ShareDO shareDO = shareService.getShare(share);
        ResponseResult<Object> validSHare = shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        if (validSHare != null) return validSHare;
        return shareService.accessShare(shareDO, pageIndex, pageSize);
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
    public ResponseResult<Object> accessShareOpenDir(HttpServletRequest request, @RequestParam String share, @RequestParam String fileId, Integer pageIndex, Integer pageSize) {
        ShareDO shareDO = shareService.getShare(share);
        ResponseResult<Object> validSHare = shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        if (validSHare != null) return validSHare;
        return shareService.accessShareOpenDir(shareDO, fileId, pageIndex, pageSize);
    }

    @Operation(summary = "打包下载")
    @GetMapping("/public/s/packageDownload")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public void publicPackageDownload(HttpServletRequest request, HttpServletResponse response, @RequestParam String shareId, @RequestParam String[] fileIds) {
        ResponseResult<Object> validShare = shareService.validShare(request.getParameter(Constants.SHARE_TOKEN), shareId);
        if (validShare != null) {
            response(response, invalid());
            return;
        }
        if (fileIds != null && fileIds.length > 0) {
            List<String> fileIdList = Arrays.asList(fileIds);
            fileService.publicPackageDownload(request, response, fileIdList);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    private void response(HttpServletResponse response, String msg) {
        try (OutputStream out = response.getOutputStream()) {
            out.write(msg.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }


    @Operation(summary = "显示缩略图")
    @GetMapping("/articles/s/view/thumbnail")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> articlesThumbnail(String id) {
        return thumbnail(id, null);
    }

    @Operation(summary = "显示缩略图")
    @GetMapping("/public/s/view/thumbnail")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> publicThumbnail(String id, HttpServletRequest request) {
        return thumbnail(id, request);
    }

    private ResponseEntity<Object> thumbnail(String id, HttpServletRequest request) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.thumbnail(id, null);
        if (fileInterceptor.isNotAllowAccess(file.orElse(null), request)) {
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
    public ResponseResult<Object> preview(HttpServletRequest request, @RequestParam String shareId, @RequestParam String fileId) {
        ShareDO shareDO = shareService.getShare(shareId);
        ResponseResult<Object> validSHare = shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        if (validSHare != null) return validSHare;
        ConsumerDO consumer = userService.userInfoById(shareDO.getUserId());
        if (consumer == null) {
            return ResultUtil.warning(SHARE_EXPIRED);
        }
        return ResultUtil.success(fileService.getById(fileId, consumer.getUsername()));
    }

    @Operation(summary = "根据id获取分享的文件信息")
    @GetMapping("/public/file_info/{fileId}/{shareId}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> getFileById(HttpServletRequest request, @PathVariable String fileId, @PathVariable String shareId) {
        ResponseResult<Object> validSHare = shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareId);
        if (validSHare != null) return validSHare;
        return ResultUtil.success(fileService.getById(fileId));
    }

}
