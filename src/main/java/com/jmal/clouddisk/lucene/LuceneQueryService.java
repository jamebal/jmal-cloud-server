package com.jmal.clouddisk.lucene;

import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Lucene 精确查询
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LuceneQueryService {

    private final SearcherManager searcherManager;

    public boolean existsSha256(String fileIndexHash) {
        IndexSearcher indexSearcher = null;
        try {
            searcherManager.maybeRefresh();
            indexSearcher = searcherManager.acquire();
            Term term = new Term(Constants.ETAG, fileIndexHash);
            Query query = new TermQuery(term);
            TopDocs topDocs = indexSearcher.search(query, 1);
            return topDocs.totalHits.value() > 0;
        } catch (IOException e) {
            log.error("检查 {} 是否存在失败", Constants.ETAG, e);
        } finally {
            if (indexSearcher != null) {
                try {
                    searcherManager.release(indexSearcher);
                } catch (IOException e) {
                    log.error("释放搜索器失败", e);
                }
            }
        }
        return false;
    }

    public Set<String> findByTagId(String tagId) {
        Term term = new Term(LuceneService.FIELD_TAG_ID, tagId);
        Query query = new TermQuery(term);
        return find(query);
    }

    public Set<String> find(Query query) {
        IndexSearcher indexSearcher = null;
        Set<String> fileIds = new HashSet<>();
        try {
            searcherManager.maybeRefresh();
            indexSearcher = searcherManager.acquire();
            TopDocs topDocs = indexSearcher.search(query, Integer.MAX_VALUE);
            StoredFields storedFields = indexSearcher.storedFields();
            for (ScoreDoc hit : topDocs.scoreDocs) {
                Document doc = storedFields.document(hit.doc);
                String id = doc.get("id");
                if (id != null) {
                    fileIds.add(id);
                }
            }
        } catch (IOException e) {
            log.error("查询失败, query: {}", query.toString(), e);
        } finally {
            if (indexSearcher != null) {
                try {
                    searcherManager.release(indexSearcher);
                } catch (IOException e) {
                    log.error("释放搜索器失败", e);
                }
            }
        }
        return fileIds;
    }
}
