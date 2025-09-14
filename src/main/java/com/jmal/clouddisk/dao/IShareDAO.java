package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IShareDAO {

    ShareDO save(ShareDO share);

    void updateSubShare(String id, Boolean isPrivacy, String extractionCode, List<String> subShareFileIdList);

    boolean existsByShortId(String shortId);

    ShareDO findByFileId(String fileId);

    ShareDO findByFatherShareId(String fatherShareId);

    ShareDO findById(String shareId);

    boolean existsSubShare(List<String> shareIdList);

    ShareDO findByShortId(String shortId);

    Page<ShareDO> findShareList(UploadApiParamDTO upload);

    void removeByFileIdIn(List<String> fileIdList);

    List<ShareDO> findAllAndRemove(List<String> shareIds);

    void removeByFatherShareId(String id);

    void removeByUserId(String userId);

    void setFileNameByFileId(String fileId, String newFileName);
}
