package power.ai.guide.modules.knowledgebase.model;

/**
 * 知识库分块导出项（供 ragas 测试集生成使用）。
 */
public record KnowledgeBaseChunkDTO(
    String content,
    String sectionPath,
    String heading
) {}
