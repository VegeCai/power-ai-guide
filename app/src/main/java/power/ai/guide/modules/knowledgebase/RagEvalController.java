package power.ai.guide.modules.knowledgebase;

import power.ai.guide.common.result.Result;
import power.ai.guide.modules.knowledgebase.model.QueryRequest;
import power.ai.guide.modules.knowledgebase.model.RagEvalDocument;
import power.ai.guide.modules.knowledgebase.model.RagEvalResponse;
import power.ai.guide.modules.knowledgebase.service.RagEvalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RAG 评测控制器
 * 供 ragas 等评测工具调用：检索+生成答案，或导出知识库分块供测试集生成。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "RAG评测", description = "供 ragas 等评测工具调用的检索+生成与分块导出接口")
public class RagEvalController {

    private final RagEvalService ragEvalService;

    @PostMapping("/api/rag-eval")
    public Result<RagEvalResponse> evaluate(@Valid @RequestBody QueryRequest request) {
        return Result.success(ragEvalService.evaluate(request));
    }

    @GetMapping("/api/rag-eval/documents")
    public Result<List<RagEvalDocument>> exportDocuments(
            @RequestParam(value = "kbIds", required = false) List<Long> kbIds) {
        return Result.success(ragEvalService.exportDocuments(kbIds));
    }
}
