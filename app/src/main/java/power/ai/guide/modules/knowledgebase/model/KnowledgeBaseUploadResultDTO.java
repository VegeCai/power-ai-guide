package power.ai.guide.modules.knowledgebase.model;

/**
 * 批量上传单个文件的结果 DTO
 * 成功时 success=true，knowledgeBase/storage 字段有值；
 * 失败时 success=false，errorMessage 有值，其余字段为 null。
 */
public record KnowledgeBaseUploadResultDTO(
    String fileName,
    boolean success,
    boolean duplicate,
    Long knowledgeBaseId,
    String name,
    String category,
    Long documentTypeId,
    Long fileSize,
    Integer contentLength,
    String fileKey,
    String fileUrl,
    String errorMessage
) {
}
