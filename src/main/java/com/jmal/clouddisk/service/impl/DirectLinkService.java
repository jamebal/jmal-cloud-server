package com.jmal.clouddisk.service.impl;

import cn.hutool.core.codec.Base64;
import com.jmal.clouddisk.dao.IDirectLinkDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.DirectLink;
import com.jmal.clouddisk.model.file.FileDocument;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
@Slf4j
public class DirectLinkService {

    public static final String COLLECTION_NAME = "direct_links";
    public static final String MARK = "mark";
    private final CommonFileService commonFileService;
    private final UserLoginHolder userLoginHolder;
    private final IDirectLinkDAO directLinkDAO;

    public String createDirectLink(String fileId) {
        String userId = userLoginHolder.getUserId();
        checkOwnership(fileId, userId);
        DirectLink directLink = getDirectLink(fileId);
        if (directLink != null) {
            return directLink.getMark();
        }
        String mark = generateMark();
        Completable.fromAction(() ->  upsertDirectLink(fileId, mark, userId)).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
        return mark;
    }

    public String resetDirectLink(String fileId) {
        String userId = userLoginHolder.getUserId();
        checkOwnership(fileId, userId);
        String mark = generateMark();
        Completable.fromAction(() ->  upsertDirectLink(fileId, mark, userId)).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
        return mark;
    }

    public String resetAllDirectLink(String fileId) {
        checkOwnership(fileId, userLoginHolder.getUserId());
        directLinkDAO.removeByUserId(userLoginHolder.getUserId());
        return resetDirectLink(fileId);
    }

    public String getFileIdByMark(String mark) {
        DirectLink directLink = directLinkDAO.findByMark(mark);
        if (directLink == null) {
            return null;
        }
        return directLink.getFileId();
    }

    private void upsertDirectLink(String fileId, String mark, String userId) {
        directLinkDAO.updateByFileId(fileId, mark, userId, LocalDateTime.now());
    }

    private String generateMark() {
        String mark;
        while (true) {
            mark = Base64.encodeUrlSafe(new SecureRandom().generateSeed(12));
            if (!isExistsMark(mark)) {
                return mark;
            }
        }
    }

    private boolean isExistsMark(String mark) {
       return directLinkDAO.existsByMark(mark);
    }

    private DirectLink getDirectLink(String fileId) {
        return directLinkDAO.findByFileId(fileId);
    }

    /**
     * 检查文件所有权
     * @param fileId 文件id
     * @param userId 用户id
     */
    private void checkOwnership(String fileId, String userId) {
        FileDocument fileDocument = commonFileService.getById(fileId);
        if (fileDocument == null) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS);
        }
        if (!fileDocument.getUserId().equals(userId)) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED);
        }
    }

}
