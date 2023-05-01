package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.config.FileProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class VideoController {

    @Autowired
    FileProperties fileProperties;

    @GetMapping("/video/hls/{username}/{videoCacheDir}/{fileMd5}/{filename}.m3u8")
    public ResponseEntity<UrlResource> m3u8(@PathVariable String username, @PathVariable String videoCacheDir, @PathVariable String fileMd5, @PathVariable String filename) throws IOException {
        Path m3u8Path = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, videoCacheDir, fileMd5, filename + ".m3u8");
        UrlResource videoResource = new UrlResource(m3u8Path.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .body(videoResource);
    }

    @GetMapping("/video/hls/{username}/{videoCacheDir}/{fileMd5}/{filename}-{index}.ts")
    public ResponseEntity<UrlResource> ts(@PathVariable String username, @PathVariable String videoCacheDir, @PathVariable String fileMd5, @PathVariable String filename, @PathVariable String index
    ) throws IOException {
        Path m3u8Path = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, videoCacheDir, fileMd5, filename + "-" + index + ".ts");
        UrlResource videoResource = new UrlResource(m3u8Path.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .body(videoResource);
    }

    @GetMapping("/public/video/hls/{username}/{videoCacheDir}/{fileMd5}/{filename}.m3u8")
    public ResponseEntity<UrlResource> publicM3u8(@PathVariable String username, @PathVariable String videoCacheDir, @PathVariable String fileMd5, @PathVariable String filename, HttpServletRequest request) throws IOException {
        return m3u8(username, videoCacheDir, fileMd5, filename);
    }

    @GetMapping("/public/video/hls/{username}/{videoCacheDir}/{fileMd5}/{filename}-{index}.ts")
    public ResponseEntity<UrlResource> publicTs(@PathVariable String username, @PathVariable String videoCacheDir, @PathVariable String fileMd5, @PathVariable String filename, @PathVariable String index
            , HttpServletRequest request) throws IOException {
        return ts(username, videoCacheDir, fileMd5, filename, index);
    }

}
