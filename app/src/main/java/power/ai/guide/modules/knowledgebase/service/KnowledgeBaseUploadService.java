package power.ai.guide.modules.knowledgebase.service;

import power.ai.guide.common.exception.BusinessException;
import power.ai.guide.common.exception.ErrorCode;
import power.ai.guide.infrastructure.file.FileHashService;
import power.ai.guide.infrastructure.file.FileStorageService;
import power.ai.guide.infrastructure.file.FileValidationService;
import power.ai.guide.modules.knowledgebase.listener.VectorizeStreamProducer;
import power.ai.guide.modules.knowledgebase.model.KnowledgeBaseBatchUploadResponse;
import power.ai.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import power.ai.guide.modules.knowledgebase.model.KnowledgeBaseUploadResultDTO;
import power.ai.guide.modules.knowledgebase.model.VectorStatus;
import power.ai.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识库上传服务
 * 处理知识库上传、解析的业务逻辑
 * 向量化改为异步处理，通过 Redis Stream 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseUploadService {

    private final KnowledgeBaseParseService parseService;
    private final KnowledgeBasePersistenceService persistenceService;
    private final FileStorageService storageService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final FileValidationService fileValidationService;
    private final FileHashService fileHashService;
    private final VectorizeStreamProducer vectorizeStreamProducer;
    private final DocumentTypeService documentTypeService;

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    
    /**
     * 上传知识库文件
     *
     * @param file 知识库文件
     * @param name 知识库名称（可选，如果为空则从文件名提取）
     * @param category 分类（可选）
     * @param documentTypeId 文档类型ID（必选）
     * @return 上传结果和存储信息（包含duplicate字段，表示是否为重复上传）
     */
    public Map<String, Object> uploadKnowledgeBase(MultipartFile file, String name, String category,
                                                   Long documentTypeId) {
        return processSingleFile(file, name, category, documentTypeId);
    }

    /**
     * 批量上传知识库文件
     * 逐文件复用单文件流程，单个文件失败不影响其他文件
     *
     * @param files 知识库文件列表
     * @param category 分类（可选，整批共享）
     * @param documentTypeId 文档类型ID（必选，整批共享）
     * @return 每个文件的处理结果汇总
     */
    public KnowledgeBaseBatchUploadResponse uploadKnowledgeBaseBatch(List<MultipartFile> files, String category,
                                                                     Long documentTypeId) {
        List<KnowledgeBaseUploadResultDTO> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename();
            try {
                Map<String, Object> result = processSingleFile(file, null, category, documentTypeId);
                results.add(toSuccessResult(fileName, result));
                successCount++;
            } catch (Exception e) {
                log.warn("批量上传单个文件失败: {}, 原因: {}", fileName, e.getMessage(), e);
                String message = e.getMessage() != null ? e.getMessage() : "上传失败: " + e.getClass().getSimpleName();
                results.add(new KnowledgeBaseUploadResultDTO(
                    fileName, false, false, null, null, null, null, null, null, null, null, message));
                failedCount++;
            }
        }

        return new KnowledgeBaseBatchUploadResponse(files.size(), successCount, failedCount, results);
    }

    /**
     * 将单文件流程结果 Map 转为批量结果 DTO（兼容重复文件 Map 无 category 字段的情况）
     */
    @SuppressWarnings("unchecked")
    private KnowledgeBaseUploadResultDTO toSuccessResult(String fileName, Map<String, Object> result) {
        Map<String, Object> kb = (Map<String, Object>) result.get("knowledgeBase");
        Map<String, Object> storage = (Map<String, Object>) result.get("storage");
        boolean duplicate = (boolean) result.get("duplicate");
        return new KnowledgeBaseUploadResultDTO(
            fileName,
            true,
            duplicate,
            ((Number) kb.get("id")).longValue(),
            (String) kb.get("name"),
            (String) kb.get("category"),
            kb.get("documentTypeId") instanceof Number n ? n.longValue() : null,
            ((Number) kb.get("fileSize")).longValue(),
            ((Number) kb.get("contentLength")).intValue(),
            (String) storage.get("fileKey"),
            (String) storage.get("fileUrl"),
            null
        );
    }

    /**
     * 单文件处理流程：验证 → 类型检测 → 去重 → 解析 → 存储 → 入库 → 异步向量化
     */
    private Map<String, Object> processSingleFile(MultipartFile file, String name, String category,
                                                  Long documentTypeId) {
        // 0. 校验文档类型（上传必须选择文档类型）
        if (documentTypeId == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_REQUIRED, "上传文档必须选择文档类型");
        }
        documentTypeService.getEntity(documentTypeId);

        // 1. 验证文件
        fileValidationService.validateFile(file, MAX_FILE_SIZE, "知识库");

        String fileName = file.getOriginalFilename();
        log.info("收到知识库上传请求: {}, 大小: {} bytes, category: {}, documentTypeId: {}",
            fileName, file.getSize(), category, documentTypeId);

        // 2. 验证文件类型
        String contentType = parseService.detectContentType(file);
        validateContentType(contentType, fileName);

        // 3. 检查知识库是否已存在（去重）
        String fileHash = fileHashService.calculateHash(file);
        Optional<KnowledgeBaseEntity> existingKb = knowledgeBaseRepository.findByFileHash(fileHash);
        if (existingKb.isPresent()) {
            log.info("检测到重复知识库: hash={}", fileHash);
            return persistenceService.handleDuplicateKnowledgeBase(existingKb.get(), fileHash);
        }

        // 4. 解析知识库文本（用于向量化）
        String content = parseService.parseContent(file);
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法从文件中提取文本内容，请确保文件格式正确");
        }

        // 5. 保存文件到RustFS
        String fileKey = storageService.uploadKnowledgeBase(file);
        String fileUrl = storageService.getFileUrl(fileKey);
        log.info("知识库已存储到RustFS: {}", fileKey);

        // 6. 保存知识库元数据到数据库（状态为 PENDING）
        KnowledgeBaseEntity savedKb = persistenceService.saveKnowledgeBase(
            file, name, category, documentTypeId, fileKey, fileUrl, fileHash);

        // 7. 发送向量化任务到 Redis Stream（异步处理）
        vectorizeStreamProducer.sendVectorizeTask(savedKb.getId(), content);

        log.info("知识库上传完成，向量化任务已入队: {}, kbId={}", fileName, savedKb.getId());

        // 8. 返回结果（状态为 PENDING，前端可轮询获取最新状态）
        return Map.of(
            "knowledgeBase", Map.of(
                "id", savedKb.getId(),
                "name", savedKb.getName(),
                "category", savedKb.getCategory() != null ? savedKb.getCategory() : "",
                "documentTypeId", savedKb.getDocumentTypeId() != null ? savedKb.getDocumentTypeId() : 0L,
                "fileSize", savedKb.getFileSize(),
                "contentLength", content.length(),
                "vectorStatus", VectorStatus.PENDING.name()
            ),
            "storage", Map.of(
                "fileKey", fileKey,
                "fileUrl", fileUrl
            ),
            "duplicate", false
        );
    }

    /**
     * 验证文件类型
     */
    private void validateContentType(String contentType, String fileName) {
        fileValidationService.validateContentType(
            contentType,
            fileName,
            fileValidationService::isKnowledgeBaseMimeType,
            fileValidationService::isMarkdownExtension,
            "不支持的文件类型: " + contentType + "，支持的类型：PDF、DOCX、DOC、TXT、MD等"
        );
    }
    
    /**
     * 重新向量化知识库（手动重试）
     * 从 RustFS 重新下载文件并发送向量化任务
     *
     * @param kbId 知识库ID
     */
    public void revectorize(Long kbId) {
        KnowledgeBaseEntity kb = knowledgeBaseRepository.findById(kbId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));

        log.info("开始重新向量化知识库: kbId={}, name={}", kbId, kb.getName());

        // 1. 下载文件并解析内容
        String content = parseService.downloadAndParseContent(kb.getStorageKey(), kb.getOriginalFilename());
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法从文件中提取文本内容");
        }

        // 2. 更新状态为 PENDING（通过单独的 Service 保证事务生效）
        persistenceService.updateVectorStatusToPending(kbId);

        // 3. 发送向量化任务到 Stream
        vectorizeStreamProducer.sendVectorizeTask(kbId, content);

        log.info("重新向量化任务已发送: kbId={}", kbId);
    }
}

