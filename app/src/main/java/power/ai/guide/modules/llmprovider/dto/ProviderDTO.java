package power.ai.guide.modules.llmprovider.dto;

import lombok.Builder;

@Builder
public record ProviderDTO(
    String id,
    String baseUrl,
    String maskedApiKey,
    String model,
    String embeddingModel,
    Integer embeddingDimensions,
    boolean supportsEmbedding,
    String rerankModel,
    boolean supportsRerank,
    String rerankUrl,
    String rerankApiStyle,
    Double temperature,
    boolean defaultChatProvider,
    boolean defaultEmbeddingProvider,
    boolean defaultRerankProvider
) {}
