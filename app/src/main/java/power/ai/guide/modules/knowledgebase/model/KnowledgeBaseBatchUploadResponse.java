package power.ai.guide.modules.knowledgebase.model;

import java.util.List;

/**
 * 知识库批量上传响应 DTO
 */
public record KnowledgeBaseBatchUploadResponse(
    int total,
    int successCount,
    int failedCount,
    List<KnowledgeBaseUploadResultDTO> results
) {
}
