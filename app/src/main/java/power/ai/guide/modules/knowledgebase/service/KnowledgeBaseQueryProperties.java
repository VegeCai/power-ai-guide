package power.ai.guide.modules.knowledgebase.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.rag")
public class KnowledgeBaseQueryProperties {

    private Rewrite rewrite = new Rewrite();
    private Search search = new Search();
    private History history = new History();
    private Hybrid hybrid = new Hybrid();
    private Rerank rerank = new Rerank();
    private String systemPromptPath = "classpath:prompts/knowledgebase-query-system.st";
    private String userPromptPath = "classpath:prompts/knowledgebase-query-user.st";
    private String rewritePromptPath = "classpath:prompts/knowledgebase-query-rewrite.st";

    @Data
    public static class Rewrite {
        private boolean enabled = true;
    }

    @Data
    public static class Search {
        private int shortQueryLength = 4;
        private int topkShort = 20;
        private int topkMedium = 12;
        private int topkLong = 8;
        private double minScoreShort = 0.25;
        private double minScoreDefault = 0.28;
    }

    @Data
    public static class History {
        private boolean enabled = true;
        private int maxMessages = 10;
    }

    /**
     * 稀疏-稠密混合召回配置（线性加权融合 S = α·S_sparse + (1-α)·S_dense）。
     */
    @Data
    public static class Hybrid {
        private double alpha = 0.3;
        private int recallTopK = 50;
    }

    /**
     * 交叉编码器重排序配置（召回 Top-50 → 精排 Top-5）。
     * 模型由默认排序 Provider 的 rerankModel 提供（见 LlmProviderRegistry）。
     */
    @Data
    public static class Rerank {
        private boolean enabled = true;
        private int topN = 5;
    }
}
