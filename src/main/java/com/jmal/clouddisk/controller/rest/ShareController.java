package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.FileInterceptor;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
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
@RequiredArgsConstructor
public class ShareController {

    public static final String SHARE_EXPIRED = "该分享已过期";

    private final IShareService shareService;

    private final IFileService fileService;

    private final IUserService userService;

    private final FileInterceptor fileInterceptor;

    private final WebOssService webOssService;

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
        shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        return shareService.accessShare(shareDO, pageIndex, pageSize);
    }

    @Operation(summary = "获取分享者信息")
    @GetMapping("/public/get/sharer")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<SharerDTO> getSharer(@RequestParam String shareId) {
        return shareService.getSharer(shareId);
    }

    @Operation(summary = "访问分享链接里的目录")
    @GetMapping("public/access-share/open")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> accessShareOpenDir(HttpServletRequest request, @RequestParam String share, @RequestParam String fileId, Integer pageIndex, Integer pageSize) {
        ShareDO shareDO = shareService.getShare(share);
        shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        return shareService.accessShareOpenDir(shareDO, fileId, pageIndex, pageSize);
    }

    @Operation(summary = "打包下载")
    @GetMapping("/public/s/packageDownload")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public void publicPackageDownload(HttpServletRequest request, HttpServletResponse response, @RequestParam String shareId, @RequestParam String[] fileIds) {
        shareService.validShare(request.getParameter(Constants.SHARE_TOKEN), shareId);
        if (fileIds != null && fileIds.length > 0) {
            List<String> fileIdList = Arrays.asList(fileIds);
            fileService.publicPackageDownload(request, response, fileIdList);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
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

    @Operation(summary = "显示缩略图")
    @GetMapping("/public/s/view/thumbnail/{filename}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> publicThumbnailName(String id, HttpServletRequest request) {
        return publicThumbnail(id, request);
    }

    @Operation(summary = "显示缩略图(媒体封面)")
    @GetMapping("/public/s/view/cover")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> coverOfMedia(String id, String name) {
        ResultUtil.checkParamIsNull(id, name);
        Optional<FileDocument> file = fileService.coverOfMedia(id, name);
        return fileService.getObjectResponseEntity(file);
    }

    private ResponseEntity<Object> thumbnail(String id, HttpServletRequest request) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.thumbnail(id);
        if (fileInterceptor.isNotAllowAccess(file.orElse(null), request)) {
            return null;
        }
        String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
        if (ossPath != null) {
            return webOssService.thumbnail(ossPath, id);
        }
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + ContentDisposition.builder("attachment")
                                .filename(UriUtils.encode(fileDocument.getName(), StandardCharsets.UTF_8)))
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
    public ResponseResult<Object> preview(HttpServletRequest request, @RequestParam String shareId, @RequestParam String fileId, Boolean content) {
        ConsumerDO consumerDO = validShare(request, shareId);
        Path prePth = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            return ResultUtil.success(webOssService.readToText(ossPath, prePth, content));
        }
        return ResultUtil.success(fileService.getById(fileId, consumerDO.getUsername(), content));
    }

    @Operation(summary = "流式读取simText文件")
    @GetMapping("/public/s/preview/text/stream")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<StreamingResponseBody> previewTextStream(HttpServletRequest request, @RequestParam String shareId, @RequestParam String fileId) {
        ConsumerDO consumerDO = validShare(request, shareId);
        Path prePth = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        StreamingResponseBody responseBody;
        if (ossPath != null) {
            responseBody = webOssService.readToTextStream(ossPath, prePth);
        } else {
            responseBody = fileService.getStreamById(fileId, consumerDO.getUsername());
        }
        return new ResponseEntity<>(responseBody, HttpStatus.OK);
    }

    private ConsumerDO validShare(HttpServletRequest request, String shareId) {
        ShareDO shareDO = shareService.getShare(shareId);
        shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        ConsumerDO consumer = userService.userInfoById(shareDO.getUserId());
        if (consumer == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), SHARE_EXPIRED);
        }
        return consumer;
    }

    @Operation(summary = "根据id获取分享的文件信息")
    @GetMapping("/public/file_info")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> getFileById(HttpServletRequest request, @RequestParam String fileId, @RequestParam String shareId) {
        shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareId);
        return ResultUtil.success(fileService.getById(fileId));
    }

}
