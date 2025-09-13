package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.ITrashDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.TrashRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.trash.TrashOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.Trash;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.TrashEntityDO;
import com.jmal.clouddisk.service.impl.CommonFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class TrashDAOJpaImpl implements ITrashDAO {

    private final TrashRepository trashRepository;

    private final FileMetadataRepository fileMetadataRepository;

    private final IWriteService writeService;


    @Override
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
    public void saveAll(List<Trash> trashList) {
        try {
            writeService.submit(new TrashOperation.CreateAll(trashList)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public FileDocument findAndRemoveById(String trashFileId) {
        TrashEntityDO trashEntityDO = trashRepository.findById(trashFileId).orElse(null);
        if (trashEntityDO != null) {
            try {
                writeService.submit(new TrashOperation.DeleteById(trashFileId)).get(10, TimeUnit.SECONDS);
                return trashEntityDO.toFileDocument();
            } catch (Exception e) {
                throw new CommonException(e.getMessage());
            }
        }
        return null;
    }

    @Override
    public List<String> findAllIdsAndRemove() {
        List<String> ids = trashRepository.findAllIds();
        if (!ids.isEmpty()) {
            try {
                writeService.submit(new TrashOperation.DeleteAll(ids)).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new CommonException(e.getMessage());
            }
        }
        return ids;
    }

}
