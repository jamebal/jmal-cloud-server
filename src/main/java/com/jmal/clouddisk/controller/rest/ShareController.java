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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @Description share
 * @Author jmal
 * @Date 2020-03-17 16:22
 */
@Tag(name = "文件分享")
@RestController
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

    @Operation(summary = "生成shareToken")
    @GetMapping("/share/generate/share-token")
    @Permission("cloud:file:list")
    public ResponseResult<Object> generateShareToken(@RequestParam String fileId) {
        return shareService.generateShareToken(fileId);
    }

    @Operation(summary = "获取分享信息")
    @GetMapping("/get/share")
    @Permission("cloud:file:list")
    @LogOperatingFun
    public ResponseResult<ShareDO> getShare(@RequestParam String shareId) {
        return ResultUtil.success(shareService.getShare(shareId));
    }

    @Operation(summary = "获取分享信息")
    @GetMapping("/get/share/by/fileId")
    @Permission("cloud:file:list")
    @LogOperatingFun
    public ResponseResult<ShareDO> getShareByFileId(@RequestParam String fileId) {
        return ResultUtil.success(shareService.getShareByFileId(fileId));
    }

    @Operation(summary = "是否含有子分享")
    @GetMapping("/share/has-sub-share")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Boolean> hasSubShare(@RequestParam List<String> shareIds) {
        return ResultUtil.success(shareService.hasSubShare(shareIds));
    }

    @Operation(summary = "文件夹下是否含有子分享")
    @GetMapping("/share/folder-sub-share")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Boolean> folderSubShare(@RequestParam String fileId) {
        return ResultUtil.success(shareService.folderSubShare(fileId));
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
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> shareList(UploadApiParamDTO upload) {
        return shareService.shareList(upload);
    }

    @Operation(summary = "挂载文件夹")
    @PutMapping("/share/mount-folder")
    @Permission("cloud:file:upload")
    public ResponseResult<Object> mountFile(@RequestBody UploadApiParamDTO upload) {
        shareService.mountFile(upload);
        return ResultUtil.success();
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
    public ResponseResult<Object> accessShare(HttpServletRequest request, @RequestParam String share, Integer pageIndex, Integer pageSize, Boolean showFolderSize) {
        ShareDO shareDO = shareService.getShare(share);
        shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        return shareService.accessShare(shareDO, pageIndex, pageSize, showFolderSize);
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
    public ResponseResult<Object> accessShareOpenDir(HttpServletRequest request, @RequestParam String share, @RequestParam String fileId, Integer pageIndex, Integer pageSize, Boolean showFolderSize) {
        ShareDO shareDO = shareService.getShare(share);
        shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        return shareService.accessShareOpenDir(shareDO, fileId, pageIndex, pageSize, showFolderSize);
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

    @Operation(summary = "打包下载")
    @GetMapping("/public/s/{fileId}/packageDownload/{filename}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public void publicPackageDownloadOne(HttpServletRequest request, HttpServletResponse response, @PathVariable String fileId, @PathVariable String filename) {
        FileDocument fileDocument = fileService.getById(fileId);
        if (fileInterceptor.isNotAllowAccess(fileDocument, request)) {
            return;
        }
        List<String> fileIdList = Collections.singletonList(fileId);
        fileService.publicPackageDownload(request, response, fileIdList);
    }

    @Operation(summary = "显示缩略图")
    @GetMapping("/articles/s/view/thumbnail")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> articlesThumbnail(String id, Boolean showCover) {
        return thumbnail(id, showCover, null);
    }

    @Operation(summary = "显示缩略图")
    @GetMapping("/public/s/view/thumbnail")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> publicThumbnail(String id, Boolean showCover, HttpServletRequest request) {
        return thumbnail(id, showCover, request);
    }

    @Operation(summary = "获取dwg文件对应的mxweb文件")
    @GetMapping("/public/s/view/mxweb/{fileId}/{shareId}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> publicGetMxweb(HttpServletRequest request, @PathVariable String shareId, @PathVariable String fileId) {
        validShare(request, shareId);
        Optional<FileDocument> file = fileService.getMxweb(fileId);
        return file.map(fileService::getObjectResponseEntity).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    @Operation(summary = "显示缩略图")
    @GetMapping("/public/s/view/thumbnail/{filename}")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> publicThumbnailName(String id, Boolean showCover, HttpServletRequest request) {
        return publicThumbnail(id, showCover, request);
    }

    @Operation(summary = "显示缩略图(媒体封面)")
    @GetMapping("/public/s/view/cover")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> coverOfMedia(String id, String name) {
        ResultUtil.checkParamIsNull(id, name);
        Optional<FileDocument> file = fileService.coverOfMedia(id, name);
        return file.map(fileService::getObjectResponseEntity).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    private ResponseEntity<Object> thumbnail(String id, Boolean showCover, HttpServletRequest request) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.thumbnail(id, showCover);
        if (fileInterceptor.isNotAllowAccess(file.orElse(null), request)) {
            return null;
        }
        String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
        if (ossPath != null) {
            return webOssService.thumbnail(ossPath, id);
        }
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
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
        validShare(request, shareId);
        Path prePth = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            return ResultUtil.success(webOssService.readToText(ossPath, prePth, content));
        }
        return ResultUtil.success(fileService.getById(fileId, content));
    }

    @Operation(summary = "流式读取simText文件")
    @GetMapping("/public/s/preview/text/stream")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<StreamingResponseBody> previewTextStream(HttpServletRequest request, @RequestParam String shareId, @RequestParam String fileId) {
        validShare(request, shareId);
        Path prePth = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        StreamingResponseBody responseBody;
        if (ossPath != null) {
            responseBody = webOssService.readToTextStream(ossPath, prePth);
        } else {
            responseBody = fileService.getStreamById(fileId);
        }
        return new ResponseEntity<>(responseBody, HttpStatus.OK);
    }

    private void validShare(HttpServletRequest request, String shareId) {
        ShareDO shareDO = shareService.getShare(shareId);
        shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareDO);
        ConsumerDO consumer = userService.userInfoById(shareDO.getUserId());
        if (consumer == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), SHARE_EXPIRED);
        }
    }

    @Operation(summary = "根据id获取分享的文件信息")
    @GetMapping("/public/file_info")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> getFileById(HttpServletRequest request, @RequestParam String fileId, @RequestParam String shareId) {
        shareService.validShare(request.getHeader(Constants.SHARE_TOKEN), shareId);
        return ResultUtil.success(fileService.getById(fileId));
    }

    @Operation(summary = "挂用户获取分享文件信息")
    @GetMapping("/mount/file_info")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Map<String, String>> getMountFileInfo(@RequestParam String fileId, @RequestParam String fileUserId) {
        return ResultUtil.success(shareService.getMountFileInfo(fileId, fileUserId));
    }

    @Operation(summary = "挂用户获取目录Id")
    @GetMapping("/mount/folder/id")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<String> getMountFolderId(@RequestParam String otherFileId, @RequestParam String fileUsername, @RequestParam String path) {
        return ResultUtil.success(shareService.getMountFolderId(path, fileUsername, otherFileId));
    }

}
