package power.ai.guide.modules.knowledgebase.model;

import java.time.LocalDateTime;

/**
 * 文档类型 DTO
 */
public record DocumentTypeDTO(
    Long id,
    String name,
    String description,
    long documentCount,
    LocalDateTime createdAt
) {
}
