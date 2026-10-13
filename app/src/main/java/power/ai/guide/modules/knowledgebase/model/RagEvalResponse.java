package power.ai.guide.modules.knowledgebase.model;

import java.util.List;

/**
 * RAG 评测响应
 * 贴合 ragas 所需字段：question / answer / contexts（即 retrieved_contexts）。
 * sources 保留完整来源元数据，便于 debug 与归因分析。
 */
public record RagEvalResponse(
    String question,
    String answer,
    List<String> contexts,
    List<RetrievalSource> sources
) {}
