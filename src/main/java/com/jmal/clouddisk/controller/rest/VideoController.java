package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.IShareService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Tag(name = "视频播放")
@Slf4j
@RestController
@RequiredArgsConstructor
public class VideoController {

    private final FileProperties fileProperties;

    private final IShareService shareService;


    @GetMapping("/video/hls/{username}/{fileMd5}.m3u8")
    public ResponseEntity<UrlResource> m3u8(@PathVariable String username, @PathVariable String fileMd5) throws IOException {
        Path m3u8Path = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getVideoTranscodeCache(), fileMd5, fileMd5 + ".m3u8");
        UrlResource videoResource = new UrlResource(m3u8Path.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .body(videoResource);
    }

    @GetMapping("/video/hls/{username}/{fileMd5}-{index}.ts")
    public ResponseEntity<UrlResource> ts(@PathVariable String username, @PathVariable String fileMd5, @PathVariable String index) throws IOException {
        Path m3u8Path = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getVideoTranscodeCache(), fileMd5, fileMd5 + "-" + index + ".ts");
        UrlResource videoResource = new UrlResource(m3u8Path.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .body(videoResource);
    }

    @GetMapping("/public/video/hls/{username}/{fileMd5}.m3u8")
    public ResponseEntity<UrlResource> publicM3u8(@PathVariable String username, @PathVariable String fileMd5, HttpServletRequest request) throws IOException {
        shareService.validShare(request);
        return m3u8(username, fileMd5);
    }

    @GetMapping("/public/video/hls/{username}/{fileMd5}-{index}.ts")
    public ResponseEntity<UrlResource> publicTs(@PathVariable String username, @PathVariable String fileMd5, @PathVariable String index, HttpServletRequest request) throws IOException {
        shareService.validShare(request);
        return ts(username, fileMd5, index);
    }

}
