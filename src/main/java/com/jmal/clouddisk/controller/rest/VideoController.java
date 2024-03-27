package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.IShareService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequiredArgsConstructor
public class VideoController {

    private final FileProperties fileProperties;

    private final IShareService shareService;


    @GetMapping("/video/hls/{username}/{fileId}.m3u8")
    public ResponseEntity<UrlResource> m3u8(@PathVariable String username, @PathVariable String fileId) throws IOException {
        Path m3u8Path = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getVideoTranscodeCache(), fileId, fileId + ".m3u8");
        UrlResource videoResource = new UrlResource(m3u8Path.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .body(videoResource);
    }

    @GetMapping("/video/hls/{username}/{fileId}-{index}.ts")
    public ResponseEntity<UrlResource> ts(@PathVariable String username, @PathVariable String fileId, @PathVariable String index) throws IOException {
        Path m3u8Path = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getVideoTranscodeCache(), fileId, fileId + "-" + index + ".ts");
        UrlResource videoResource = new UrlResource(m3u8Path.toUri());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .body(videoResource);
    }

    @GetMapping("/public/video/hls/{shareId}/{shareToken}/{username}/{fileId}.m3u8")
    public ResponseEntity<UrlResource> publicM3u8(@PathVariable String username, @PathVariable String fileId, @PathVariable String shareId, @PathVariable String shareToken) throws IOException {
        shareService.validShare(shareToken, shareId);
        return m3u8(username, fileId);
    }

    @GetMapping("/public/video/hls/{shareId}/{shareToken}/{username}/{fileId}-{index}.ts")
    public ResponseEntity<UrlResource> publicTs(@PathVariable String username, @PathVariable String fileId, @PathVariable String index, @PathVariable String shareId, @PathVariable String shareToken) throws IOException {
        shareService.validShare(shareToken, shareId);
        return ts(username, fileId, index);
    }

}
