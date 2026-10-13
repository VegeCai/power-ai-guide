package power.ai.guide.modules.knowledgebase.service;

import power.ai.guide.modules.knowledgebase.model.KnowledgeBaseChunkDTO;
import power.ai.guide.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import power.ai.guide.modules.knowledgebase.model.QueryRequest;
import power.ai.guide.modules.knowledgebase.model.QueryResponse;
import power.ai.guide.modules.knowledgebase.model.RagEvalDocument;
import power.ai.guide.modules.knowledgebase.model.RagEvalResponse;
import power.ai.guide.modules.knowledgebase.model.RetrievalSource;
import power.ai.guide.modules.knowledgebase.model.VectorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 评测服务
 * 复用生产问答链路，将检索来源扁平化为 ragas 所需的 contexts 结构。
 * 同时提供知识库分块导出，供 ragas 测试集生成使用。
 */
@Service
@RequiredArgsConstructor
public class RagEvalService {

    private final KnowledgeBaseQueryService queryService;
    private final KnowledgeBaseVectorService vectorService;
    private final KnowledgeBaseListService listService;

    public RagEvalResponse evaluate(QueryRequest request) {
        QueryResponse response = queryService.queryKnowledgeBase(request);
        List<String> contexts = response.sources().stream()
            .map(RetrievalSource::content)
            .toList();
        return new RagEvalResponse(request.question(), response.answer(), contexts, response.sources());
    }

    /**
     * 导出知识库分块，供 ragas TestsetGenerator 合成测试集。
     * kbIds 为空时默认导出全部已完成向量化的知识库。
     */
    public List<RagEvalDocument> exportDocuments(List<Long> kbIds) {
        List<Long> targetIds = (kbIds == null || kbIds.isEmpty())
            ? listService.listKnowledgeBases(VectorStatus.COMPLETED, null).stream()
                .map(KnowledgeBaseListItemDTO::id)
                .toList()
            : kbIds;

        List<String> names = listService.getKnowledgeBaseNames(targetIds);
        Map<Long, String> nameById = new HashMap<>();
        for (int i = 0; i < targetIds.size(); i++) {
            nameById.put(targetIds.get(i), names.get(i));
        }

        List<RagEvalDocument> documents = new ArrayList<>();
        for (Long kbId : targetIds) {
            String kbName = nameById.getOrDefault(kbId, "未知知识库");
            for (KnowledgeBaseChunkDTO chunk : vectorService.listChunks(kbId)) {
                documents.add(new RagEvalDocument(
                    kbId, kbName, chunk.content(), chunk.sectionPath(), chunk.heading()));
            }
        }
        return documents;
    }
}
