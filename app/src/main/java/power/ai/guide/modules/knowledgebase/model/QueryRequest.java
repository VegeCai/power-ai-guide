package power.ai.guide.modules.knowledgebase.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 知识库查询请求
 */
public record QueryRequest(
    List<Long> knowledgeBaseIds,  // 支持多个知识库（可与文档类型取并集）

    List<Long> documentTypeIds,   // 文档类型ID列表（可选，与知识库取并集）

    @NotBlank(message = "问题不能为空")
    String question
) {
    /**
     * 兼容单知识库查询（向后兼容）
     */
    public QueryRequest(Long knowledgeBaseId, String question) {
        this(List.of(knowledgeBaseId), null, question);
    }

    /**
     * 兼容按知识库列表查询（向后兼容）
     */
    public QueryRequest(List<Long> knowledgeBaseIds, String question) {
        this(knowledgeBaseIds, null, question);
    }
}

