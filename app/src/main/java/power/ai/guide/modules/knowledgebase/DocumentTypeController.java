package power.ai.guide.modules.knowledgebase;

import power.ai.guide.common.result.Result;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeDTO;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeRequest.CreateRequest;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeRequest.UpdateRequest;
import power.ai.guide.modules.knowledgebase.service.DocumentTypeService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档类型控制器
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "文档类型管理", description = "文档类型的增删改查")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    /**
     * 获取所有文档类型
     */
    @GetMapping("/api/document-types")
    public Result<List<DocumentTypeDTO>> list() {
        return Result.success(documentTypeService.list());
    }

    /**
     * 创建文档类型
     */
    @PostMapping("/api/document-types")
    public Result<DocumentTypeDTO> create(@Valid @RequestBody CreateRequest request) {
        return Result.success(documentTypeService.create(request));
    }

    /**
     * 更新文档类型
     */
    @PutMapping("/api/document-types/{id}")
    public Result<DocumentTypeDTO> update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return Result.success(documentTypeService.update(id, request));
    }

    /**
     * 删除文档类型
     */
    @DeleteMapping("/api/document-types/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentTypeService.delete(id);
        return Result.success(null);
    }
}
