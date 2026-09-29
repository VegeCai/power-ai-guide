package power.ai.guide.modules.knowledgebase.service;

import power.ai.guide.common.chunking.ChunkingProperties;
import power.ai.guide.common.chunking.HierarchicalChunker;
import power.ai.guide.common.exception.BusinessException;
import power.ai.guide.common.exception.ErrorCode;
import power.ai.guide.common.transaction.TransactionalExecutor;
import power.ai.guide.modules.knowledgebase.repository.VectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库向量存储服务
 * 负责文档分块、向量化和检索
 */
@Slf4j
@Service
public class KnowledgeBaseVectorService {
    
    /**
     * 阿里云 DashScope Embedding API 批量大小限制
     */
    private static final int MAX_BATCH_SIZE = 10;
    private static final String TEMP_KB_ID_PREFIX = "pending:";
    private static final String METADATA_KB_ID = "kb_id";
    private static final String METADATA_TARGET_KB_ID = "kb_target_id";
    private static final String METADATA_VECTOR_JOB_ID = "kb_vector_job_id";
    private final VectorStore vectorStore;
    private final VectorRepository vectorRepository;
    private final TransactionalExecutor transactionalExecutor;
    private final HierarchicalChunker hierarchicalChunker;
    private final ChunkingProperties chunkingProperties;

    @Autowired
    public KnowledgeBaseVectorService(
        VectorStore vectorStore,
        VectorRepository vectorRepository,
        TransactionalExecutor transactionalExecutor,
        HierarchicalChunker hierarchicalChunker,
        ChunkingProperties chunkingProperties
    ) {
        this.vectorStore = vectorStore;
        this.vectorRepository = vectorRepository;
        this.transactionalExecutor = transactionalExecutor;
        this.hierarchicalChunker = hierarchicalChunker;
        this.chunkingProperties = chunkingProperties;
    }

    KnowledgeBaseVectorService(VectorStore vectorStore, VectorRepository vectorRepository) {
        this(vectorStore, vectorRepository, null, null, null);
    }

    /**
     * 将知识库内容向量化并存储
     * @param knowledgeBaseId 知识库ID
     * @param content 知识库文本内容
     */
    public int vectorizeAndStore(Long knowledgeBaseId, String content) {
        String jobId = null;
        try {
            if (knowledgeBaseId == null) {
                throw new IllegalArgumentException("knowledgeBaseId不能为空");
            }
            jobId = UUID.randomUUID().toString();
            log.info("开始向量化知识库: kbId={}, jobId={}, contentLength={}",
                knowledgeBaseId, jobId, content.length());

            // 1. 将文本分块（层级切块）
            List<Document> chunks = chunkContent(content);

            log.info("文本分块完成: {} 个chunks", chunks.size());
            
            // 2. 为每个 chunk 添加临时 metadata，成功后再提升为正式 kb_id。
            applyPendingMetadata(chunks, knowledgeBaseId, jobId);

            // 3. 分批向量化并存储（阿里云 DashScope API 限制 batch size <= 10）
            int totalChunks = chunks.size();
            int batchCount = (totalChunks + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE; // 向上取整
            log.info("开始分批向量化: 总共 {} 个chunks，分 {} 批处理，每批最多 {} 个",
                    totalChunks, batchCount, MAX_BATCH_SIZE);
            for (int i = 0; i < batchCount; i++) {
                int start = i * MAX_BATCH_SIZE;
                int end = Math.min(start + MAX_BATCH_SIZE, totalChunks);
                List<Document> batch = chunks.subList(start, end);
                log.debug("处理第 {}/{} 批: chunks {}-{}", i + 1, batchCount, start + 1, end);
                vectorStore.add(batch);
            }
            activateVectorJob(knowledgeBaseId, jobId);
            log.info("知识库向量化完成: kbId={}, jobId={}, chunks={}, batches={}",
                    knowledgeBaseId, jobId, totalChunks, batchCount);
            return totalChunks;
        } catch (Exception e) {
            cleanupPendingVectorJob(knowledgeBaseId, jobId);
            log.error("向量化知识库失败: kbId={}, jobId={}, error={}",
                knowledgeBaseId, jobId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
                "向量化知识库失败: " + e.getMessage());
        }
    }

    private void applyPendingMetadata(List<Document> chunks, Long knowledgeBaseId, String jobId) {
        String pendingKbId = TEMP_KB_ID_PREFIX + knowledgeBaseId + ":" + jobId;
        chunks.forEach(chunk -> {
            chunk.getMetadata().put(METADATA_KB_ID, pendingKbId);
            chunk.getMetadata().put(METADATA_TARGET_KB_ID, knowledgeBaseId.toString());
            chunk.getMetadata().put(METADATA_VECTOR_JOB_ID, jobId);
        });
    }

    /**
     * 按配置切分文本：默认层级切块，禁用时回退到扁平 TokenTextSplitter。
     */
    private List<Document> chunkContent(String content) {
        if (hierarchicalChunker != null && (chunkingProperties == null || chunkingProperties.isEnabled())) {
            return hierarchicalChunker.chunk(content);
        }
        return TokenTextSplitter.builder().build().apply(List.of(new Document(content)));
    }

    /**
     * 基于多个知识库进行相似度搜索
     * 
     * @param query 查询文本
     * @param knowledgeBaseIds 知识库ID列表（如果为空则搜索所有）
     * @param topK 返回top K个结果
     * @return 相关文档列表
     */
    public List<Document> similaritySearch(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        log.info("向量相似度搜索: query={}, kbIds={}, topK={}, minScore={}",
            query, knowledgeBaseIds, topK, minScore);
        
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(Math.max(topK, 1));

            if (minScore > 0) {
                builder.similarityThreshold(minScore);
            }

            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                builder.filterExpression(buildKbFilterExpression(knowledgeBaseIds));
            }

            List<Document> results = vectorStore.similaritySearch(builder.build());
            if (results == null) {
                return List.of();
            }

            // Apply topK limiting in case VectorStore returns more than requested
            List<Document> limitedResults = results.stream()
                .limit(topK)
                .collect(Collectors.toList());

            log.info("搜索完成: 找到 {} 个相关文档", limitedResults.size());
            return limitedResults;
            
        } catch (Exception e) {
            log.warn("向量搜索前置过滤失败，回退到本地过滤: {}", e.getMessage());
            return similaritySearchFallback(query, knowledgeBaseIds, topK, minScore);
        }
    }

    private List<Document> similaritySearchFallback(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        try {
            // 回退检索仍保留 topK/minScore，避免兜底路径引入过多弱相关命中
            SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(Math.max(topK * 3, topK));
            if (minScore > 0) {
                builder.similarityThreshold(minScore);
            }

            List<Document> allResults = vectorStore.similaritySearch(builder.build());
            if (allResults == null || allResults.isEmpty()) {
                return List.of();
            }

            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                allResults = allResults.stream()
                    .filter(doc -> isDocInKnowledgeBases(doc, knowledgeBaseIds))
                    .collect(Collectors.toList());
            }

            List<Document> results = allResults.stream()
                .limit(topK)
                .collect(Collectors.toList());

            log.info("回退检索完成: 找到 {} 个相关文档", results.size());
            return results;
        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED,
                "向量搜索失败: " + e.getMessage());
        }
    }

    private boolean isDocInKnowledgeBases(Document doc, List<Long> knowledgeBaseIds) {
        Object kbId = doc.getMetadata().get("kb_id");
        if (kbId == null) {
            return false;
        }
        try {
            Long kbIdLong = kbId instanceof Long
                ? (Long) kbId
                : Long.parseLong(kbId.toString());
            return knowledgeBaseIds.contains(kbIdLong);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String buildKbFilterExpression(List<Long> knowledgeBaseIds) {
        String values = knowledgeBaseIds.stream()
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .map(id -> "'" + id + "'")
            .collect(Collectors.joining(", "));
        return "kb_id in [" + values + "]";
    }

    /**
     * 混合召回：稠密路（pgvector cosine）+ 稀疏路（pg_trgm word_similarity）线性融合。
     * S_total = α·S_sparse + (1-α)·S_dense，去重后按融合分降序，截断到 recallTopK。
     * 任一路失败不阻断整条链路（sparse 失败退化为纯 dense，dense 失败退化为纯 sparse）。
     */
    public List<Document> hybridSearch(String query, List<Long> kbIds, int recallTopK, double alpha) {
        List<Document> dense = denseSearch(query, kbIds, recallTopK);
        List<Map<String, Object>> sparse = sparseSearch(query, kbIds, recallTopK);
        List<Document> fused = fuse(dense, sparse, alpha);
        List<Document> limited = fused.stream()
            .limit(Math.max(1, recallTopK))
            .collect(Collectors.toList());
        log.info("混合召回完成: query={}, dense={}, sparse={}, fused={}",
            query, dense.size(), sparse.size(), limited.size());
        return limited;
    }

    private List<Document> denseSearch(String query, List<Long> kbIds, int recallTopK) {
        try {
            return similaritySearch(query, kbIds, recallTopK, 0.0);
        } catch (Exception e) {
            log.warn("稠密检索失败，跳过稠密路: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> sparseSearch(String query, List<Long> kbIds, int recallTopK) {
        try {
            List<String> kbIdStrs = kbIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
            return vectorRepository.searchByTrigram(query, kbIdStrs, recallTopK);
        } catch (Exception e) {
            log.warn("稀疏检索失败，退化为纯稠密: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 线性融合两路召回：按文档 id 去重合并，融合分 = α·S_sparse + (1-α)·S_dense。
     * 稠密 Document.score 与稀疏 word_similarity 均为 0~1 相似度，可直接加权。
     */
    static List<Document> fuse(List<Document> dense, List<Map<String, Object>> sparse, double alpha) {
        Map<String, Document> docById = new LinkedHashMap<>();
        Map<String, Double> scoreById = new HashMap<>();
        double denseWeight = 1.0 - alpha;

        for (Document doc : dense) {
            String id = doc.getId();
            if (id == null) {
                continue;
            }
            docById.putIfAbsent(id, doc);
            scoreById.merge(id, denseWeight * toSimilarity(doc), Double::sum);
        }
        for (Map<String, Object> row : sparse) {
            String id = row.get("id") == null ? null : row.get("id").toString();
            if (id == null) {
                continue;
            }
            docById.computeIfAbsent(id, key -> toDocument(row));
            scoreById.merge(id, alpha * toDouble(row.get("score")), Double::sum);
        }
        return docById.keySet().stream()
            .sorted(Comparator.comparingDouble(id -> -scoreById.getOrDefault(id, 0.0)))
            .map(id -> docById.get(id).mutate().score(scoreById.get(id)).build())
            .collect(Collectors.toList());
    }

    /**
     * 取文档相似度分数：优先 Document.score（PgVectorStore 已置为 1 - distance），
     * 否则回退 metadata.distance 计算 1 - distance。
     */
    static double toSimilarity(Document doc) {
        Double score = doc.getScore();
        if (score != null) {
            return score;
        }
        Object distance = doc.getMetadata().get("distance");
        if (distance instanceof Number n) {
            return 1.0 - n.doubleValue();
        }
        return 0.0;
    }

    static Document toDocument(Map<String, Object> row) {
        String id = row.get("id") == null ? null : row.get("id").toString();
        String content = row.get("content") == null ? "" : row.get("content").toString();
        Map<String, Object> metadata = new HashMap<>();
        putString(metadata, "kb_id", row.get("kb_id"));
        putString(metadata, "section_path", row.get("section_path"));
        double score = toDouble(row.get("score"));
        return Document.builder().id(id).text(content).metadata(metadata).score(score).build();
    }

    private static void putString(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value.toString());
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                // fallthrough
            }
        }
        return 0.0;
    }

    /**
     * 删除指定知识库的所有向量数据
     * 委托给 VectorRepository 处理
     * 
     * @param knowledgeBaseId 知识库ID
     */
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        try {
            deleteByKnowledgeBaseIdStrict(knowledgeBaseId);
        } catch (Exception e) {
            log.error("删除向量数据失败: kbId={}, error={}", knowledgeBaseId, e.getMessage(), e);
            // 不抛出异常，允许继续执行其他删除操作
            // 如果确实需要严格保证，可以取消下面的注释
            // throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED, "删除向量数据失败");
        }
    }

    private void deleteByKnowledgeBaseIdStrict(Long knowledgeBaseId) {
        runVectorRepositoryMutation(() -> vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId));
    }

    private void activateVectorJob(Long knowledgeBaseId, String jobId) {
        runVectorRepositoryMutation(() -> {
            vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId);
            vectorRepository.promoteVectorJob(knowledgeBaseId, jobId);
        });
    }

    private void cleanupPendingVectorJob(Long knowledgeBaseId, String jobId) {
        if (jobId == null) {
            return;
        }
        try {
            runVectorRepositoryMutation(() -> vectorRepository.deleteByVectorJobId(jobId));
        } catch (Exception cleanupError) {
            log.warn("清理临时向量数据失败，可后续按 jobId 补偿: kbId={}, jobId={}, error={}",
                knowledgeBaseId, jobId, cleanupError.getMessage(), cleanupError);
        }
    }

    private void runVectorRepositoryMutation(Runnable action) {
        if (transactionalExecutor == null) {
            action.run();
            return;
        }
        transactionalExecutor.run(action);
    }
}
