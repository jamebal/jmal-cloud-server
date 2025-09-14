package com.jmal.clouddisk.lucene;

import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
public class CoverFileService {

    private final ImageMagickProcessor imageMagickProcessor;

    private final MongoTemplate mongoTemplate;

    /**
     * 更新文件封面
     *
     * @param fileId    文件Id
     * @param coverFile 封面文件
     */
    public void updateCoverFileDocument(String fileId, File coverFile) {
        if (coverFile == null || !coverFile.exists()) {
            return;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(new ObjectId(fileId)));
        FileDocument fileDocument = new FileDocument();
        fileDocument.setId(fileId);
        Update update = new Update();
        imageMagickProcessor.generateThumbnail(coverFile, fileDocument);
        update.set("showCover", true);
        update.set(Constants.CONTENT, fileDocument.getContent());
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

}
