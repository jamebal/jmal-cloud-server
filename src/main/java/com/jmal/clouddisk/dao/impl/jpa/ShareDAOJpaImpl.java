package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IShareDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.ShareRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.share.ShareOperation;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ShareDAOJpaImpl implements IShareDAO, IWriteCommon<ShareDO> {

    private final ShareRepository shareRepository;

    private final IWriteService writeService;


    @Override
    @Transactional
    public ShareDO save(ShareDO share) {
        return shareRepository.save(share);
    }

    @Override
    @Transactional
    public void updateSubShare(String id, Boolean isPrivacy, String extractionCode, List<String> subShareFileIdList) {
        if (Boolean.TRUE.equals(isPrivacy)) {
            shareRepository.updateToPrivacyShare(subShareFileIdList, id, extractionCode);
        } else {
            shareRepository.updateToPublicShare(subShareFileIdList, id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByShortId(String shortId) {
        return shareRepository.existsByShortId(shortId);
    }

    @Override
    @Transactional(readOnly = true)
    public ShareDO findByFileId(String fileId) {
        return shareRepository.findByFileId(fileId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public ShareDO findByFatherShareId(String fatherShareId) {
        return shareRepository.findByFatherShareId(fatherShareId);
    }

    @Override
    @Transactional(readOnly = true)
    public ShareDO findById(String shareId) {
        return shareRepository.findById(shareId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsSubShare(List<String> shareIdList) {
        return shareRepository.existsByFatherShareIdIn(shareIdList);
    }

    @Override
    @Transactional(readOnly = true)
    public ShareDO findByShortId(String shortId) {
        return shareRepository.findByShortId(shortId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShareDO> findShareList(UploadApiParamDTO upload) {
        return shareRepository.findShareList(upload.getUserId(), upload.getPageable());
    }

    @Override
    @Transactional
    public void removeByFileIdIn(List<String> fileIdList) {
        shareRepository.removeByFileIdIn(fileIdList);
    }

    @Override
    @Transactional
    public List<ShareDO> findAllAndRemove(List<String> shareIds) {
        if (shareIds == null || shareIds.isEmpty()) {
            return List.of();
        }
        List<ShareDO> sharesToDelete = shareRepository.findAllById(shareIds);
        if (sharesToDelete.isEmpty()) {
            return List.of();
        }
        List<String> foundIds = sharesToDelete.stream().map(ShareDO::getId).toList();
        shareRepository.deleteAllByIdInBatch(foundIds);
        return sharesToDelete;
    }

    @Override
    @Transactional
    public void removeByFatherShareId(String id) {
        shareRepository.removeByFatherShareId(id);
    }

    @Override
    public void removeByUserId(String userId) {
        shareRepository.removeByUserId(userId);
    }

    @Override
    public void AsyncSaveAll(Iterable<ShareDO> entities) {
        writeService.submit(new ShareOperation.CreateAll(entities));
    }
}
