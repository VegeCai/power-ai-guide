package power.ai.guide.modules.knowledgebase.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 文档类型创建/更新请求
 */
public class DocumentTypeRequest {

    public record CreateRequest(
        @NotBlank(message = "类型名称不能为空")
        String name,

        String description
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "类型名称不能为空")
        String name,

        String description
    ) {}
}
