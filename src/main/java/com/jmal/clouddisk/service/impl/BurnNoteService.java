package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.DateUnit;
import com.jmal.clouddisk.dao.BurnNoteFileService;
import com.jmal.clouddisk.dao.IBurnNoteDAO;
import com.jmal.clouddisk.dao.impl.BurnNoteCleanupService;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.BurnNoteDO;
import com.jmal.clouddisk.model.dto.BurnNoteCreateDTO;
import com.jmal.clouddisk.model.dto.BurnNoteResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class BurnNoteService {

    private final BurnNoteCleanupService burnNoteCleanupService;
    private final IBurnNoteDAO burnNoteDAO;
    private final BurnNoteFileService burnNoteFileService;
    private static final int MAX_VIEWS = 100;
    private static final int MAX_EXPIRATION_MINUTES = 1440; // 24小时

    /**
     * 创建阅后即焚笔记
     */
    @Transactional
    public String createBurnNote(BurnNoteCreateDTO dto, String userId) {
        // 参数校验
        validateCreateParams(dto);

        BurnNoteDO burnNote = new BurnNoteDO();
        burnNote.setUserId(userId);
        burnNote.setEncryptedContent(dto.getEncryptedContent());
        burnNote.setIsFile(dto.getIsFile());

        if (dto.getIsFile()) {
            burnNote.setTotalChunks(dto.getTotalChunks());
            burnNote.setFileSize(dto.getFileSize());
        }

        // 设置查看次数或过期时间
        if (dto.getViews() != null && dto.getViews() > 0) {
            burnNote.setViewsLeft(dto.getViews());
        } else if (dto.getExpirationMinutes() != null && dto.getExpirationMinutes() > 0) {
            burnNote.setExpireAt(Instant.now().plusMillis(dto.getExpirationMinutes() * DateUnit.MINUTE.getMillis()));
        } else {
            burnNote.setViewsLeft(1); // 默认1次
        }

        String id = burnNoteDAO.save(burnNote).getId();
        log.debug("创建笔记: id={}, isFile={}, userId={}", id, dto.getIsFile(), userId);

        burnNoteCleanupService.scheduleCleanupIfNecessary();

        return id;
    }

    /**
     * 读取笔记 (阅后即焚核心逻辑)
     * 读取后减少次数或删除
     */
    public BurnNoteResponseDTO consumeBurnNote(String id) {
        BurnNoteDO burnNote = burnNoteDAO.findById(id);

        if (burnNote == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "笔记不存在或已被销毁");
        }

        // 检查是否过期
        if (burnNote.getExpireAt() != null && Instant.now().isAfter(burnNote.getExpireAt())) {
            deleteBurnNote(burnNote);
            throw new CommonException(ExceptionType.WARNING.getCode(), "笔记已过期");
        }

        // 检查查看次数
        if (burnNote.getViewsLeft() != null) {
            int viewsLeft = burnNote.getViewsLeft() - 1;

            if (viewsLeft <= 0) {
                if (burnNote.getIsFile()) {
                    // 文件类型：标记为待删除
                    burnNote.setViewsLeft(0);
                    burnNoteDAO.save(burnNote);
                    log.debug("文件笔记标记为待删除: noteId={}", id);
                } else {
                    // 文本类型：立即删除
                    burnNoteDAO.deleteById(id);
                    log.debug("文本笔记已销毁: noteId={}", id);
                }
            } else {
                // 更新剩余次数
                burnNote.setViewsLeft(viewsLeft);
                burnNoteDAO.save(burnNote);
                log.debug("笔记查看次数减1: id={}, 剩余次数={}", id, viewsLeft);
            }
        }

        return new BurnNoteResponseDTO(burnNote.getEncryptedContent(), burnNote.getIsFile());
    }

    /**
     * 确认删除（前端下载完成后调用）
     */
    @Transactional
    public void confirmDelete(String noteId) {
        BurnNoteDO burnNote = burnNoteDAO.findById(noteId);
        if (burnNote != null && burnNote.getViewsLeft() != null && burnNote.getViewsLeft() == 0) {
            deleteBurnNote(burnNote);
            log.debug("确认删除笔记: noteId={}", noteId);
        }
    }

    /**
     * 检查笔记是否存在 (不减少次数)
     */
    public boolean checkNoteExists(String id) {
        BurnNoteDO burnNote = burnNoteDAO.findById(id);
        if (burnNote == null) {
            return false;
        }

        // 检查是否过期
        if (burnNote.getExpireAt() != null && Instant.now().isAfter(burnNote.getExpireAt())) {
            deleteBurnNote(burnNote);
            return false;
        }

        return true;
    }

    /**
     * 获取笔记信息（不减少次数）
     */
    public BurnNoteDO getBurnNoteById(String noteId) {
        return burnNoteDAO.findById(noteId);
    }

    /**
     * 删除笔记和文件分片
     */
    private void deleteBurnNote(BurnNoteDO burnNote) {
        if (burnNote.getIsFile()) {
            burnNoteFileService.deleteAllChunks(burnNote.getId());
        }
        burnNoteDAO.deleteById(burnNote.getId());
    }

    /**
     * 参数校验
     */
    private void validateCreateParams(BurnNoteCreateDTO dto) {
        if (dto.getIsFile()) {
            if (dto.getTotalChunks() == null || dto.getTotalChunks() <= 0) {
                throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "文件分片数无效");
            }
            if (dto.getFileSize() == null || dto.getFileSize() <= 0) {
                throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "文件大小无效");
            }
        } else {
            if (dto.getEncryptedContent() == null || dto.getEncryptedContent().isEmpty()) {
                throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "加密内容不能为空");
            }
        }

        if (dto.getViews() != null && (dto.getViews() < 1 || dto.getViews() > MAX_VIEWS)) {
            throw new CommonException(ExceptionType.PARAMETERS_VALUE.getCode(),
                    String.format("查看次数必须在 1-%d 之间", MAX_VIEWS));
        }

        if (dto.getExpirationMinutes() != null &&
                (dto.getExpirationMinutes() < 1 || dto.getExpirationMinutes() > MAX_EXPIRATION_MINUTES)) {
            throw new CommonException(ExceptionType.PARAMETERS_VALUE.getCode(),
                    String.format("过期时间必须在 1-%d 分钟之间", MAX_EXPIRATION_MINUTES));
        }
    }

}
