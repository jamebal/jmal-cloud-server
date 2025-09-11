package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.DirectLink;

import java.time.LocalDateTime;

public interface IDirectLinkDAO {

    void removeByUserId(String userId);

    DirectLink findByMark(String mark);

    void updateByFileId(String fileId, String mark, String userId, LocalDateTime now);

    boolean existsByMark(String mark);

    DirectLink findByFileId(String fileId);
}
