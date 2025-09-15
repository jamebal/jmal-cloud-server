package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IFileHistoryDAO;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.Metadata;
import com.jmal.clouddisk.model.file.FileHistoryDTO;
import com.jmal.clouddisk.service.Constants;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class FileHistoryDAOImpl implements IFileHistoryDAO {

    private static final String COLLECTION_NAME = "fs.files";
    private final MongoTemplate mongoTemplate;
    private final GridFsTemplate gridFsTemplate;

    @Override
    public void store(InputStream inputStream, String fileId, Metadata metadata) {
        gridFsTemplate.store(inputStream, fileId, metadata);
    }

    @Override
    public FileHistoryDTO getFileHistoryDTO(String fileHistoryId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileHistoryId));
        GridFSFile gridFSFile = gridFsTemplate.findOne(query);
        if (gridFSFile.getMetadata() == null) {
            return null;
        }
        return new FileHistoryDTO(gridFSFile);
    }

    @Override
    public InputStream getInputStream(String fileId, String fileHistoryId) throws IOException {
        return gridFsTemplate.getResource(fileId).getInputStream();
    }

    @Override
    public Page<GridFSBO> findPageByFileId(String fileId, Pageable pageable) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILENAME).is(fileId));
        long total = mongoTemplate.count(query, COLLECTION_NAME);
        if (total == 0) {
            return Page.empty(pageable);
        }
        query.with(pageable);
        List<GridFSBO> gridFSBOList = mongoTemplate.find(query, GridFSBO.class, COLLECTION_NAME);
        return new PageImpl<>(gridFSBOList, pageable, total);
    }

    @Override
    public void deleteAllByFileIdIn(List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILENAME).in(fileIds));
        gridFsTemplate.delete(query);
    }

    @Override
    public void deleteByIdIn(List<String> fileHistoryIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileHistoryIds));
        gridFsTemplate.delete(query);
    }

    @Override
    public void updateFileId(String sourceFileId, String destinationFileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILENAME).is(sourceFileId));
        Update update = new Update();
        update.set(Constants.FILENAME, destinationFileId);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
    }
}
