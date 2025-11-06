package com.jmal.clouddisk.lucene;

import cn.hutool.core.thread.ThreadUtil;
import com.jmal.clouddisk.dao.IFileDAO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class LuceneReconciliationService {

    private final ApplicationEventPublisher eventPublisher;
    private final SearcherManager searcherManager;
    private final IFileDAO fileDAO;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // 每批处理的文档数量
    private static final int BATCH_SIZE = 1000;

    public void startReconciliation() {
        ThreadUtil.execute(this::reconcileOrphanDocuments);
    }

    /**
     * 对账并清理 Lucene 中的孤儿索引
     */
    private void reconcileOrphanDocuments() {
        if (!isRunning.compareAndSet(false, true)) {
            log.debug("⚠️ Lucene协调任务已在运行中。跳过此次执行。");
            return;
        }
        log.debug("🚀 开始Lucene协调任务...");
        long startTime = System.currentTimeMillis();
        long totalDocsChecked = 0;
        Set<String> orphanIds = new HashSet<>();

        IndexSearcher searcher = null;
        try {
            searcherManager.maybeRefreshBlocking();
            searcher = searcherManager.acquire();
            if (searcher.getIndexReader().maxDoc() == 0) {
                log.debug("✅ Lucene索引为空。无需对账。");
                return;
            }

            // 1. 使用 MatchAllDocsQuery 来匹配所有“活文档”
            Query query = new org.apache.lucene.search.MatchAllDocsQuery();

            // 2. 使用 searchAfter 进行深度分页遍历
            TopDocs topDocs = searcher.search(query, BATCH_SIZE);
            ScoreDoc lastHit = null;

            while (topDocs != null && topDocs.scoreDocs.length > 0) {
                Set<String> luceneIdsInBatch = new HashSet<>();

                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document doc = searcher.storedFields().document(scoreDoc.doc);
                    String idValue = doc.get("id");
                    if (idValue != null) {
                        luceneIdsInBatch.add(idValue);
                    }
                    lastHit = scoreDoc; // 记录本批次的最后一个文档
                }

                log.debug("处理了 {} 个文档...", luceneIdsInBatch.size());
                totalDocsChecked += luceneIdsInBatch.size();

                if (!luceneIdsInBatch.isEmpty()) {
                    // 批量查询数据库，找出存在的 ID
                    List<String> existingDbIds = fileDAO.findByIdIn(luceneIdsInBatch);

                    // 差集操作，找出孤儿 ID
                    luceneIdsInBatch.removeAll(new HashSet<>(existingDbIds));

                    if (!luceneIdsInBatch.isEmpty()) {
                        log.debug("在本批中找到{}个孤立的文档。", luceneIdsInBatch.size());
                        orphanIds.addAll(luceneIdsInBatch);
                    }
                }

                // 获取下一批的文档
                topDocs = searcher.searchAfter(lastHit, query, BATCH_SIZE);
            }

            // 如果找到了孤儿索引，执行批量删除
            if (!orphanIds.isEmpty()) {
                log.debug("待删除的总孤儿文档数: {}", orphanIds.size());
                deleteOrphansFromIndex(orphanIds);
            } else {
                log.debug("✅ 未找到孤儿文档。索引与数据库一致。");
            }


        } catch (IOException e) {
            log.error("Lucene协调过程中出现错误。", e);
        } finally {
            if (searcher != null) {
                try {
                    searcherManager.release(searcher);
                } catch (IOException e) {
                    log.error("释放搜索器失败", e);
                }
            }
            isRunning.set(false);
        }
        long duration = System.currentTimeMillis() - startTime;
        log.debug("🏁 Lucene 协调任务已完成。检查：{} 篇文档。发现孤岛：{}。耗时：{} 毫秒",
                totalDocsChecked, orphanIds.size(), duration);
    }

    private void deleteOrphansFromIndex(Set<String> orphanIds) throws IOException {
        eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, orphanIds));
        log.debug("成功从Lucene索引中删除了{}个孤立的文档。", orphanIds.size());
    }
}
