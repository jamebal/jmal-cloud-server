package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.dao.BurnNoteFileService;
import com.jmal.clouddisk.model.BurnNoteDO;
import com.jmal.clouddisk.model.dto.BurnNoteCreateDTO;
import com.jmal.clouddisk.model.dto.BurnNoteResponseDTO;
import com.jmal.clouddisk.model.dto.BurnNoteVO;
import com.jmal.clouddisk.model.query.QueryBaseDTO;
import com.jmal.clouddisk.service.impl.BurnNoteService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Tag(name = "阅后即焚")
@RestController
@RequiredArgsConstructor
@Slf4j
public class BurnNoteController {

    private final BurnNoteService burnNoteService;
    private final BurnNoteFileService burnNoteFileService;
    private final UserLoginHolder userLoginHolder;

    @Operation(summary = "阅后即焚笔记列表")
    @Permission("cloud:file:list")
    @GetMapping("/burn-notes/list")
    public ResponseResult<List<BurnNoteVO>> listNote(QueryBaseDTO queryBaseDTO) {
        String userId = userLoginHolder.getUserId();
        return ResultUtil.success(burnNoteService.burnNoteList(queryBaseDTO, userId));
    }

    @Operation(summary = "删除阅后即焚笔记")
    @Permission("cloud:file:delete")
    @DeleteMapping("/burn-notes/delete/{noteId}")
    public ResponseResult<Void> deleteNote(@PathVariable String noteId) {
        String userId = userLoginHolder.getUserId();
        burnNoteService.deleteBurnNote(noteId, userId);
        return ResultUtil.success();
    }

    @Operation(summary = "创建阅后即焚笔记")
    @PostMapping("/burn-notes/create")
    public ResponseResult<String> createNote(@RequestBody BurnNoteCreateDTO dto) {
        String userId = userLoginHolder.getUserId();
        if (userId == null) {
            userId = "anonymous";
        }
        return ResultUtil.success(burnNoteService.createBurnNote(dto, userId));
    }

    @Operation(summary = "上传文件分片")
    @PostMapping("/burn-notes/{noteId}/chunks/{chunkIndex}")
    public ResponseResult<Void> uploadChunk(
            @PathVariable String noteId,
            @PathVariable Integer chunkIndex,
            @RequestParam("file") MultipartFile file) {
        try {
            BurnNoteDO burnNote = burnNoteService.getBurnNoteById(noteId);
            ResponseResult<Void> validation = validateAndGetFileNote(burnNote, chunkIndex);
            if (validation.getCode() != 0) {
                return validation;
            }

            burnNoteFileService.saveChunk(noteId, chunkIndex, file);

            log.debug("上传分片: noteId={}, chunk={}/{}",
                    noteId, chunkIndex + 1, burnNote.getTotalChunks());

            return ResultUtil.success();
        } catch (IOException e) {
            log.error("上传分片失败: noteId={}, chunk={}", noteId, chunkIndex, e);
            return ResultUtil.error("上传分片失败: " + e.getMessage());
        }
    }

    @Operation(summary = "下载文件分片")
    @GetMapping("/public/burn-notes/{noteId}/chunks/{chunkIndex}")
    public ResponseEntity<?> downloadChunk(@PathVariable String noteId, @PathVariable Integer chunkIndex) {
        try {
            BurnNoteDO burnNote = burnNoteService.getBurnNoteById(noteId);
            ResponseResult<Void> validation = validateAndGetFileNote(burnNote, chunkIndex);
            if (validation.getCode() != 0) {
                return ResponseEntity.badRequest().body(validation);
            }

            File chunkFile = burnNoteFileService.getChunkFile(noteId, chunkIndex);
            Resource resource = new FileSystemResource(chunkFile);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"chunk_" + chunkIndex + "\"")
                    .body(resource);

        } catch (IOException e) {
            log.error("下载分片失败: noteId={}, chunk={}", noteId, chunkIndex, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseResult<Void> validateAndGetFileNote(BurnNoteDO burnNote, int chunkIndex) {
        if (burnNote == null) {
            return ResultUtil.error("笔记不存在");
        }

        if (!burnNote.getIsFile()) {
            return ResultUtil.error("不是文件类型笔记");
        }

        if (chunkIndex < 0 || chunkIndex >= burnNote.getTotalChunks()) {
            return ResultUtil.error("分片索引无效");
        }

        return ResultUtil.success();
    }

    @Operation(summary = "检查笔记是否存在")
    @GetMapping("/public/burn-notes/{id}/check")
    public ResponseResult<Boolean> checkNote(@PathVariable String id) {
        boolean exists = burnNoteService.checkNoteExists(id);
        return ResultUtil.success(exists);
    }

    @Operation(summary = "读取并消费笔记 (阅后即焚)")
    @DeleteMapping("/public/burn-notes/{id}")
    public ResponseResult<BurnNoteResponseDTO> consumeNote(@PathVariable String id) {
        BurnNoteResponseDTO response = burnNoteService.consumeBurnNote(id);
        return ResultUtil.success(response);
    }

    @Operation(summary = "确认删除")
    @DeleteMapping("/public/burn-notes/{noteId}/confirm-delete")
    public ResponseResult<Void> confirmDelete(@PathVariable String noteId) {
        burnNoteService.confirmDelete(noteId);
        return ResultUtil.success();
    }

}
