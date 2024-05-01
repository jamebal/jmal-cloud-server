package com.jmal.clouddisk.controller.rest;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.URLUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.oss.web.WebOssCommonService;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


/**
 * @author jmal
 * @Description 云文件管理控制器
 * @Author jmal
 * @Date 2020-01-27 12:59
 */
@Tag(name = "文件管理")
@RestController
public class FileController {

    @Autowired
    private IFileService fileService;

    @Autowired
    private WebOssService webOssService;

    @Autowired
    AuthInterceptor authInterceptor;

    @Autowired
    UserLoginHolder userLoginHolder;

    @Autowired
    IUserService service;

    @Operation(summary = "根据id获取文件信息")
    @GetMapping("/file_info")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<FileDocument> getFileById(@RequestParam String id) {
        return ResultUtil.success(fileService.getById(id));
    }

    @Operation(summary = "文件列表")
    @GetMapping("/list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> list(UploadApiParamDTO upload) {
        return fileService.listFiles(upload);
    }

    @Operation(summary = "查找下级目录")
    @GetMapping("/query-file-tree")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> queryFileTree(UploadApiParamDTO upload, String fileId) {
        return fileService.queryFileTree(upload, fileId);
    }

    @Operation(summary = "搜索文件")
    @GetMapping("/search-file")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<FileIntroVO>> searchFile(UploadApiParamDTO upload, String keyword) {
        return fileService.searchFile(upload, keyword);
    }

    @Operation(summary = "搜索文件并打开文件夹")
    @GetMapping("/search-file-open")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> searchFileAndOpenDir(UploadApiParamDTO upload, String id, String folder) {
        return fileService.searchFileAndOpenDir(upload, id, folder);
    }

    @Operation(summary = "图片上传(Typora自定义命令上传图片接口)")
    @PostMapping("/img-upload")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public String imgUpload(HttpServletRequest request, MultipartFile file) {
        if (file == null) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "缺少文件参数, file");
        }
        String filepath = request.getHeader(Constants.FILE_PATH);
        if (CharSequenceUtil.isBlank(filepath)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "headers里缺少参数, filepath: 远程目标文件夹, 例如: '/Image/Typora/Public/Images'");
        }
        String baseUrl = request.getHeader("baseurl");
        if (CharSequenceUtil.isBlank(baseUrl)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "headers里缺少参数, baseUrl: 远程服务器地址, 例如: 'https://www.jmal.top/api'");
        }
        return fileService.imgUpload(baseUrl, filepath, file);
    }

    @Operation(summary = "文件上传")
    @PostMapping("upload")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> uploadPost(UploadApiParamDTO upload) throws IOException {
        return fileService.upload(upload);
    }

    @Operation(summary = "文件夹上传")
    @PostMapping("upload-folder")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> uploadFolder(UploadApiParamDTO upload) {
        return fileService.uploadFolder(upload);
    }

    @Operation(summary = "新建文件夹")
    @PostMapping("new_folder")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> newFolder(UploadApiParamDTO upload) {
        return fileService.newFolder(upload);
    }

    @Operation(summary = "检查文件/分片是否存在")
    @GetMapping("upload")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> checkUpload(UploadApiParamDTO upload) throws IOException {
        return fileService.checkChunkUploaded(upload);
    }

    @Operation(summary = "检查文件是否存在")
    @PostMapping("checkExist")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> checkFileExist(UploadApiParamDTO upload) throws IOException {
        return fileService.checkFileExist(upload);
    }

    @Operation(summary = "合并文件")
    @PostMapping("merge")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<Object> merge(UploadApiParamDTO upload) throws IOException {
        return fileService.merge(upload);
    }

    @Operation(summary = "读取simText文件")
    @GetMapping("/preview/text")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> previewText(@RequestParam String id, @RequestParam String path, @RequestParam String fileName, Boolean content) {
        String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
        if (ossPath != null) {
            Path prePth = Paths.get(WebOssCommonService.getUsernameByOssPath(ossPath), path, fileName);
            return ResultUtil.success(webOssService.readToText(ossPath, prePth, content));
        }
        return ResultUtil.success(fileService.getById(id, content));
    }

    @Operation(summary = "流式读取simText文件")
    @GetMapping("/preview/text/stream")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<StreamingResponseBody> previewTextStream(@RequestParam String id, @RequestParam String path, @RequestParam String fileName) {
        String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
        StreamingResponseBody responseBody;
        if (ossPath != null) {
            Path prePth = Paths.get(WebOssCommonService.getUsernameByOssPath(ossPath), path, fileName);
            responseBody = webOssService.readToTextStream(ossPath, prePth);
        } else {
            responseBody = fileService.getStreamById(id);
        }
        return new ResponseEntity<>(responseBody, HttpStatus.OK);
    }

    @Operation(summary = "根据path读取simText文件")
    @GetMapping("/preview/path/text")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> previewTextByPath(@RequestParam String path, @RequestParam String username) {
        Path prePth = Paths.get(username, path);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            return ResultUtil.success(webOssService.readToText(ossPath, prePth, false));
        }
        return ResultUtil.success(fileService.previewTextByPath(URLUtil.decode(path), username));
    }

    @Operation(summary = "根据path流式读取simText文件")
    @GetMapping("/preview/path/text/stream")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<StreamingResponseBody> previewTextByPathStream(@RequestParam String path, @RequestParam String username) {
        Path prePth = Paths.get(username, path);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        StreamingResponseBody responseBody;
        if (ossPath != null) {
            responseBody = webOssService.readToTextStream(ossPath, prePth);
        } else {
            responseBody = fileService.previewTextByPathStream(URLUtil.decode(path), username);
        }
        return new ResponseEntity<>(responseBody, HttpStatus.OK);
    }

    @Operation(summary = "是否允许下载")
    @GetMapping("/isAllowDownload")
    @Permission("cloud:file:download")
    public ResponseResult<Object> isAllowDownload() {
        return ResultUtil.success(true);
    }

    @Operation(summary = "打包下载")
    @GetMapping("/packageDownload")
    @LogOperatingFun
    @Permission("cloud:file:download")
    public void packageDownload(HttpServletRequest request, HttpServletResponse response, @RequestParam String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> fileIdList = Arrays.asList(fileIds);
            fileService.packageDownload(request, response, fileIdList);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "显示缩略图")
    @GetMapping("/view/thumbnail")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> thumbnail(@RequestParam String id) {
        String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
        if (ossPath != null) {
            return webOssService.thumbnail(ossPath, id);
        }
        Optional<FileDocument> file = fileService.thumbnail(id);
        return file.map(fileService::getObjectResponseEntity).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    @Operation(summary = "显示缩略图")
    @GetMapping("/view/thumbnail/{filename}")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> thumbnailName(@RequestParam String id) {
        return thumbnail(id);
    }

    @Operation(summary = "显示缩略图(媒体封面)")
    @GetMapping("/view/cover")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseEntity<Object> coverOfMedia(String id, String name) {
        ResultUtil.checkParamIsNull(id, name);
        Optional<FileDocument> file = fileService.coverOfMedia(id, name);
        return file.map(fileService::getObjectResponseEntity).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    @Operation(summary = "收藏文件或文件夹")
    @PostMapping("/favorite")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> favorite(@RequestParam String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.favorite(list);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "设置文件标签")
    @PostMapping("/setTag")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> setTag(@RequestBody @Validated EditTagDTO editTagDTO) {
        return fileService.setTag(editTagDTO);
    }

    @Operation(summary = "设为公共文件")
    @PutMapping("/setPublic")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> setPublic(@RequestParam String fileId) {
        fileService.setPublic(fileId);
        return ResultUtil.success();
    }

    @Operation(summary = "取消收藏")
    @PostMapping("/unFavorite")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> unFavorite(@RequestParam String[] fileIds) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.unFavorite(list);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/delete")
    @LogOperatingFun
    @Permission("cloud:file:delete")
    public ResponseResult<Object> delete(@RequestParam String username, @RequestParam String[] fileIds, @RequestParam String currentDirectory) {
        if (fileIds != null && fileIds.length > 0) {
            List<String> list = Arrays.asList(fileIds);
            return fileService.delete(username, currentDirectory, list, userLoginHolder.getUsername());
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "重命名")
    @GetMapping("/rename")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> rename(@RequestParam String newFileName, @RequestParam String username, @RequestParam String id, String folder) {
        return fileService.rename(URLUtil.decode(newFileName), username, id, folder);
    }

    @Operation(summary = "移动文件/文件夹")
    @GetMapping("/move")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> move(UploadApiParamDTO upload, @RequestParam String[] froms, String to) throws IOException {
        if (froms != null && froms.length > 0) {
            List<String> list = Arrays.asList(froms);
            return fileService.move(upload, list, to);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "复制文件/文件夹")
    @GetMapping("/copy")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> copy(UploadApiParamDTO upload, @RequestParam String[] froms, String to) throws IOException {
        if (froms != null && froms.length > 0) {
            List<String> list = Arrays.asList(froms);
            return fileService.copy(upload, list, to);
        } else {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
        }
    }

    @Operation(summary = "创建副本")
    @GetMapping("/duplicate")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> duplicate(@RequestParam String fileId, @RequestParam String newFilename) throws IOException {
        return fileService.duplicate(fileId, newFilename);
    }

    @Operation(summary = "解压zip文件")
    @GetMapping("/unzip")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> unzip(@RequestParam String fileId, String destFileId) {
        return fileService.unzip(fileId, destFileId);
    }

    @Operation(summary = "获取目录下的文件")
    @GetMapping("/listfiles")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> listFiles(@RequestParam String path, @RequestParam String username, Boolean tempDir) {
        Boolean dir = tempDir;
        if (dir == null) {
            dir = false;
        }
        return fileService.listFiles(URLUtil.decode(path), username, dir);
    }

    @Operation(summary = "获取上级文件列表")
    @GetMapping("/upper-level-list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<Object> upperLevelList(@RequestParam String path, @RequestParam String username) {
        return fileService.upperLevelList(URLUtil.decode(path), username);
    }

    @Operation(summary = "根据path删除文件/文件夹")
    @DeleteMapping("/delFile")
    @LogOperatingFun
    @Permission("cloud:file:delete")
    public ResponseResult<Object> delFile(@RequestParam String path, @RequestParam String username) {
        return fileService.delFile(URLUtil.decode(path), username);
    }

    @Operation(summary = "根据path重命名")
    @GetMapping("/rename/path")
    @LogOperatingFun
    @Permission("cloud:file:update")
    public ResponseResult<Object> renameByPath(@RequestParam String newFileName, @RequestParam String username, @RequestParam String path) {
        return fileService.renameByPath(URLUtil.decode(newFileName), username, URLUtil.decode(path));
    }

    @Operation(summary = "根据path添加文件/文件夹")
    @PostMapping("/addfile")
    @LogOperatingFun
    @Permission("cloud:file:upload")
    public ResponseResult<FileIntroVO> addFile(@RequestParam String fileName, @RequestParam Boolean isFolder, @RequestParam String username, @RequestParam String parentPath, String folder) {
        return fileService.addFile(URLUtil.decode(fileName), isFolder, username, URLUtil.decode(parentPath), folder);
    }
}
