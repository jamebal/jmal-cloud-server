package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IShareDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.ShareRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.share.ShareOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ShareDAOJpaImpl implements IShareDAO, IWriteCommon<ShareDO> {

    private final ShareRepository shareRepository;

    private final IWriteService writeService;


    @Override
    public ShareDO save(ShareDO share) {
        CompletableFuture<ShareDO> future = writeService.submit(new ShareOperation.Create(share));
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error saving ShareDO: {}", e.getMessage(), e);
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void updateSubShare(String id, Boolean isPrivacy, String extractionCode, List<String> subShareFileIdList) {
        writeService.submit(new ShareOperation.UpdateSubShare(subShareFileIdList, id, isPrivacy, extractionCode));
    }

    @Override
    public boolean existsByShortId(String shortId) {
        return shareRepository.existsByShortId(shortId);
    }

    @Override
    public ShareDO findByFileId(String fileId) {
        return shareRepository.findByFileId(fileId).orElse(null);
    }

    @Override
    public ShareDO findByFatherShareId(String fatherShareId) {
        return shareRepository.findByFatherShareId(fatherShareId);
    }

    @Override
    public ShareDO findById(String shareId) {
        return shareRepository.findById(shareId).orElse(null);
    }

    @Override
    public boolean existsSubShare(List<String> shareIdList) {
        return shareRepository.existsByFatherShareIdIn(shareIdList);
    }

    @Override
    public ShareDO findByShortId(String shortId) {
        return shareRepository.findByShortId(shortId).orElse(null);
    }

    @Override
    public Page<ShareDO> findShareList(UploadApiParamDTO upload) {
        return shareRepository.findShareList(upload.getUserId(), upload.getPageable());
    }

    @Override
    public void removeByFileIdIn(List<String> fileIdList) {
        writeService.submit(new ShareOperation.RemoveByFileIdIn(fileIdList));
    }

    @Override
    public List<ShareDO> findAllAndRemove(List<String> shareIds) {
        if (shareIds == null || shareIds.isEmpty()) {
            return List.of();
        }
        List<ShareDO> sharesToDelete = shareRepository.findAllById(shareIds);
        if (sharesToDelete.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = sharesToDelete.stream().map(ShareDO::getId).toList();
        writeService.submit(new ShareOperation.DeleteAllByIdInBatch(foundIds));
        return sharesToDelete;
    }

    @Override
    public void removeByFatherShareId(String id) {
        writeService.submit(new ShareOperation.RemoveByFatherShareId(id));
    }

    @Override
    public void removeByUserId(String userId) {
        writeService.submit(new ShareOperation.removeByUserId(userId));
    }

    @Override
    public void setFileNameByFileId(String fileId, String newFileName) {
        writeService.submit(new ShareOperation.SetFileNameByFileId(fileId, newFileName));
    }

    @Override
    public void AsyncSaveAll(Iterable<ShareDO> entities) {
        writeService.submit(new ShareOperation.CreateAll(entities));
    }
}
