package com.jmal.clouddisk.controller;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.Consumer;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.ShareBO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @Description share
 * @Author jmal
 * @Date 2020-03-17 16:22
 */
@Api(tags = "文件分享")
@RestController
@Slf4j
public class ShareController {

    @Autowired
    IShareService shareService;

    @Autowired
    IFileService fileService;

    @Autowired
    IUserService userService;

    @ApiOperation("该分享已失效")
    @GetMapping("/public/s/invalid")
    @ResponseBody
    public String invalid() {
        return "该分享已失效";
    }

    @ApiOperation("生成分享链接")
    @PostMapping("/share/generate")
    @ResponseBody
    public ResponseResult<Object> generateLink(@RequestBody ShareBO share) {
        ResultUtil.checkParamIsNull(share.getFileId(), share.getUserId());
        return shareService.generateLink(share);
    }

    @ApiOperation("取消分享")
    @DeleteMapping("/share/cancel")
    @ResponseBody
    public ResponseResult<Object> cancelShare(String[] shareId,String userId) {
        ResultUtil.checkParamIsNull(shareId, userId);
        List<String> shareIdList = Arrays.asList(shareId);
        return shareService.cancelShare(shareIdList, userId);
    }

    @ApiOperation("分享列表")
    @GetMapping("/share/list")
    public ResponseResult<Object> shareList(UploadApiParamDTO upload) {
        return shareService.shareList(upload);
    }

    @ApiOperation("访问分享链接")
    @GetMapping("/public/access-share")
    @ResponseBody
    public ResponseResult<Object> accessShare(@RequestParam String share, Integer pageIndex, Integer pageSize) {
        ResultUtil.checkParamIsNull(share);
        return shareService.accessShare(share, pageIndex, pageSize);
    }

    @ApiOperation("访问分享链接里的目录")
    @GetMapping("public/access-share/open")
    @ResponseBody
    public ResponseResult<Object> accessShareOpenDir(@RequestParam String share, @RequestParam String fileId, Integer pageIndex, Integer pageSize) {
        ShareBO shareBO = shareService.getShare(share);
        if(!shareService.checkWhetherExpired(shareBO)){
            return ResultUtil.warning("该分享以过期");
        }
        return shareService.accessShareOpenDir(shareBO, fileId, pageIndex, pageSize );
    }

    @ApiOperation("打包下载")
    @GetMapping("/public/s/packageDownload")
    public void publicPackageDownload(HttpServletRequest request, HttpServletResponse response, @RequestParam String shareId, String[] fileIds) {
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

    @ApiOperation("显示缩略图")
    @GetMapping("/public/s/view/thumbnail")
    public ResponseEntity<Object> thumbnail(String id) {
        ResultUtil.checkParamIsNull(id);
        Optional<FileDocument> file = fileService.thumbnail(id,null);
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileDocument.getName())
                        .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileDocument.getContent().length)).header("Connection", "close")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileDocument.getContent().length))
                        .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
                        .body(fileDocument.getContent())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
    }

    @ApiOperation("读取simText文件")
    @GetMapping("/public/s/preview/text")
    public ResponseResult<Object> preview(@RequestParam String shareId,@RequestParam String fileId) {
        ShareBO shareBO = shareService.getShare(shareId);
        if(!shareService.checkWhetherExpired(shareBO)){
            return ResultUtil.warning("该分享以过期");
        }
        Consumer consumer = userService.userInfoById(shareBO.getUserId());
        if(consumer == null){
            return ResultUtil.warning("该分享以过期");
        }
        return ResultUtil.success(fileService.getById(fileId,consumer.getUsername()));
    }
}
