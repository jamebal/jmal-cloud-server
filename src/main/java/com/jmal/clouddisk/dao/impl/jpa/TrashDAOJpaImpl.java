package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.ITrashDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.TrashRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.trash.TrashOperation;
import com.jmal.clouddisk.model.file.TrashEntityDO;
import com.jmal.clouddisk.service.impl.CommonFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class TrashDAOJpaImpl implements ITrashDAO, IWriteCommon<TrashEntityDO> {

    private final TrashRepository trashRepository;

    private final FileMetadataRepository fileMetadataRepository;

    private final IWriteService writeService;


    @Override
    @Transactional(readOnly = true)
    public long getOccupiedSpace(String userId, String collectionName) {
        if (CommonFileService.COLLECTION_NAME.equals(collectionName)) {
            Long space = fileMetadataRepository.calculateTotalSizeByUserId(userId);
            return space != null ? space : 0L;
        } else if (CommonFileService.TRASH_COLLECTION_NAME.equals(collectionName)) {
            Long space = trashRepository.calculateTotalSizeByUserId(userId);
            return space != null ? space : 0L;
        }
        return 0L;
    }

    @Override
    public void AsyncSaveAll(Iterable<TrashEntityDO> entities) {
        writeService.submit(new TrashOperation.CreateAll(entities));
    }
}
