package com.jmal.clouddisk.ai;

import com.jmal.clouddisk.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 向量存储服务
 * 使用Lucene的KnnVectorField存储文档向量
 *
 * @author jmal
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jmalcloud.ai", name = "enabled", havingValue = "true")
public class VectorStoreService {

    private final FileProperties fileProperties;
    private final AiProperties aiProperties;

    private Directory vectorDirectory;
    private IndexWriter vectorIndexWriter;

    private static final String FIELD_FILE_ID = "fileId";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_CONTENT_PREVIEW = "contentPreview";

    @PostConstruct
    public void init() {
        try {
            Path vectorIndexPath = Paths.get(fileProperties.getRootDir(), fileProperties.getLuceneIndexDir(), "vector");
            Files.createDirectories(vectorIndexPath);
            vectorDirectory = FSDirectory.open(vectorIndexPath);

            IndexWriterConfig config = new IndexWriterConfig();
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            vectorIndexWriter = new IndexWriter(vectorDirectory, config);

            log.info("Vector store initialized at: {}", vectorIndexPath);
        } catch (IOException e) {
            log.error("Failed to initialize vector store: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (vectorIndexWriter != null) {
                vectorIndexWriter.close();
            }
            if (vectorDirectory != null) {
                vectorDirectory.close();
            }
        } catch (IOException e) {
            log.error("Failed to close vector store: {}", e.getMessage(), e);
        }
    }

    /**
     * 存储向量
     *
     * @param fileId        文件ID
     * @param vector        向量
     * @param contentPreview 内容预览（可选）
     */
    public void storeVector(String fileId, float[] vector, String contentPreview) {
        if (fileId == null || vector == null || vector.length == 0) {
            return;
        }

        try {
            // 先删除已存在的向量
            deleteVector(fileId);

            Document doc = new Document();
            doc.add(new StringField(FIELD_FILE_ID, fileId, Field.Store.YES));
            doc.add(new KnnFloatVectorField(FIELD_VECTOR, vector, VectorSimilarityFunction.COSINE));

            if (contentPreview != null && !contentPreview.isBlank()) {
                // 只存储前500个字符的预览
                String preview = contentPreview.length() > 500 ? contentPreview.substring(0, 500) : contentPreview;
                doc.add(new StoredField(FIELD_CONTENT_PREVIEW, preview));
            }

            vectorIndexWriter.addDocument(doc);
            vectorIndexWriter.commit();

            log.debug("Stored vector for file: {}", fileId);
        } catch (IOException e) {
            log.error("Failed to store vector for file {}: {}", fileId, e.getMessage(), e);
        }
    }

    /**
     * 存储向量（无内容预览）
     *
     * @param fileId 文件ID
     * @param vector 向量
     */
    public void storeVector(String fileId, float[] vector) {
        storeVector(fileId, vector, null);
    }

    /**
     * 删除向量
     *
     * @param fileId 文件ID
     */
    public void deleteVector(String fileId) {
        if (fileId == null) {
            return;
        }

        try {
            vectorIndexWriter.deleteDocuments(new Term(FIELD_FILE_ID, fileId));
            vectorIndexWriter.commit();
        } catch (IOException e) {
            log.error("Failed to delete vector for file {}: {}", fileId, e.getMessage(), e);
        }
    }

    /**
     * 批量删除向量
     *
     * @param fileIds 文件ID列表
     */
    public void deleteVectors(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        try {
            Term[] terms = fileIds.stream()
                    .map(id -> new Term(FIELD_FILE_ID, id))
                    .toArray(Term[]::new);
            vectorIndexWriter.deleteDocuments(terms);
            vectorIndexWriter.commit();
        } catch (IOException e) {
            log.error("Failed to delete vectors: {}", e.getMessage(), e);
        }
    }

    /**
     * 相似度搜索
     *
     * @param queryVector 查询向量
     * @param topK        返回结果数量
     * @return 相似文件ID列表和分数
     */
    public List<VectorSearchResult> searchSimilar(float[] queryVector, int topK) {
        List<VectorSearchResult> results = new ArrayList<>();

        if (queryVector == null || queryVector.length == 0) {
            return results;
        }

        try {
            DirectoryReader reader = DirectoryReader.open(vectorDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);

            KnnFloatVectorQuery query = new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, topK);
            TopDocs topDocs = searcher.search(query, topK);

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String fileId = doc.get(FIELD_FILE_ID);
                String preview = doc.get(FIELD_CONTENT_PREVIEW);

                if (fileId != null) {
                    results.add(new VectorSearchResult(fileId, scoreDoc.score, preview));
                }
            }

            reader.close();
        } catch (IOException e) {
            log.error("Failed to search vectors: {}", e.getMessage(), e);
        }

        return results;
    }

    /**
     * 检查向量是否存在
     *
     * @param fileId 文件ID
     * @return 是否存在
     */
    public boolean hasVector(String fileId) {
        if (fileId == null) {
            return false;
        }

        try {
            DirectoryReader reader = DirectoryReader.open(vectorDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);

            org.apache.lucene.search.TermQuery query = new org.apache.lucene.search.TermQuery(new Term(FIELD_FILE_ID, fileId));
            TopDocs topDocs = searcher.search(query, 1);

            reader.close();
            return topDocs.totalHits.value() > 0;
        } catch (IOException e) {
            log.debug("Failed to check vector existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 向量搜索结果
     */
    public record VectorSearchResult(String fileId, float score, String contentPreview) {
    }
}
