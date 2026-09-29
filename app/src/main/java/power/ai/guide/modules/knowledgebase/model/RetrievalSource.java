package power.ai.guide.modules.knowledgebase.model;

/**
 * 检索命中来源
 * 用于在回答下方标注引用的文档片段（文档名 + 章节路径 + 原文 + 相关度分数）。
 */
public record RetrievalSource(
    Long kbId,
    String documentName,
    String sectionPath,
    String content,
    double score
) {}
