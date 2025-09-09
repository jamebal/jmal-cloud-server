package com.jmal.clouddisk.dao.impl.mongodb.repository;

import com.jmal.clouddisk.model.ArchivesVO;
import com.jmal.clouddisk.model.file.FileDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public interface FileDocumentRepository extends MongoRepository<FileDocument, String> {

    /**
     * 按月份归档文章
     */
    @Aggregation(pipeline = {
        "{$match: { 'release': true, 'alonePage': { $exists: false } }}",
        "{$sort: { 'uploadDate': -1 }}",
        "{$project: { " +
            "name: 1, " +
            "slug: 1, " +
            "date: '$uploadDate', " +
            "day: { $dateToString: { format: '%Y-%m', date: '$uploadDate' } }" +
        "}}",
    })
    List<ArchivesVO> findArchives(Pageable pageable);

    long countByReleaseIsTrueAndAlonePageExists(Boolean release, Boolean alonePage);
}
