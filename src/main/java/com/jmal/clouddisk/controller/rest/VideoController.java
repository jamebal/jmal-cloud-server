package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.MyFileUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Tag(name = "视频播放")
@RestController
@RequiredArgsConstructor
public class VideoController {

    private final FileProperties fileProperties;

    private final IShareService shareService;


    @GetMapping("/video/hls/{username}/{fileId}.m3u8")
    public ResponseEntity<UrlResource> m3u8(@PathVariable String username, @PathVariable String fileId) throws IOException {
        return getUrlResourceResponseEntity(username, fileId, fileId + ".m3u8");
    }

    private @NotNull ResponseEntity<UrlResource> getUrlResourceResponseEntity(String username, String fileId, String suffix) throws MalformedURLException {
        Path txtPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getVideoTranscodeCache(), fileId, suffix);
        UrlResource videoResource = new UrlResource(txtPath.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, FileContentTypeUtils.getContentType(MyFileUtils.extName(suffix)))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=600")
                .body(videoResource);
    }

    @GetMapping("/video/hls/{username}/{fileId}.vtt")
    public ResponseEntity<UrlResource> vtt(@PathVariable String username, @PathVariable String fileId) throws IOException {
        return getUrlResourceResponseEntity(username, fileId, fileId + ".vtt");
    }

    @GetMapping("/video/hls/{username}/{fileId}-vtt.jpg")
    public ResponseEntity<UrlResource> vttPNG(@PathVariable String username, @PathVariable String fileId) throws IOException {
        return getUrlResourceResponseEntity(username, fileId, fileId + "-vtt.jpg");
    }

    @GetMapping("/video/hls/{username}/{fileId}-{index}.ts")
    public ResponseEntity<UrlResource> ts(@PathVariable String username, @PathVariable String fileId, @PathVariable String index) throws IOException {
        return getUrlResourceResponseEntity(username, fileId, fileId + "-" + index + ".ts");
    }

    @GetMapping("/public/video/hls/{shareId}/{shareToken}/{username}/{fileId}.m3u8")
    public ResponseEntity<UrlResource> publicM3u8(@PathVariable String username, @PathVariable String fileId, @PathVariable String shareId, @PathVariable String shareToken) throws IOException {
        shareService.validShare(shareToken, shareId);
        return m3u8(username, fileId);
    }

    @GetMapping("/public/video/hls/{shareId}/{shareToken}/{username}/{fileId}.vtt")
    public ResponseEntity<UrlResource> publicVtt(@PathVariable String username, @PathVariable String fileId, @PathVariable String shareId, @PathVariable String shareToken) throws IOException {
        shareService.validShare(shareToken, shareId);
        return vtt(username, fileId);
    }

    @GetMapping("/public/video/hls/{shareId}/{shareToken}/{username}/{fileId}-{index}.ts")
    public ResponseEntity<UrlResource> publicTs(@PathVariable String username, @PathVariable String fileId, @PathVariable String index, @PathVariable String shareId, @PathVariable String shareToken) throws IOException {
        shareService.validShare(shareToken, shareId);
        return ts(username, fileId, index);
    }

}
