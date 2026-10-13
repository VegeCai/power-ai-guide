package power.ai.guide.modules.knowledgebase.model;

/**
 * 供 ragas 测试集生成的分块文档（含知识库归属信息）。
 */
public record RagEvalDocument(
    Long kbId,
    String kbName,
    String content,
    String sectionPath,
    String heading
) {}
