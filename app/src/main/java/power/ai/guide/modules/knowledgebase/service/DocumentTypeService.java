package power.ai.guide.modules.knowledgebase.service;

import power.ai.guide.common.exception.BusinessException;
import power.ai.guide.common.exception.ErrorCode;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeDTO;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeEntity;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeRequest.CreateRequest;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeRequest.UpdateRequest;
import power.ai.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import power.ai.guide.modules.knowledgebase.repository.DocumentTypeRepository;
import power.ai.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档类型服务
 * 负责文档类型的增删改查，以及「类型ID → 文档(kb)ID」的解析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 获取所有文档类型（含每个类型下的文档数量，单次分组查询避免 N+1）
     */
    @Transactional(readOnly = true)
    public List<DocumentTypeDTO> list() {
        List<DocumentTypeEntity> types = documentTypeRepository.findAll();
        Map<Long, Long> countByTypeId = knowledgeBaseRepository.countByDocumentTypeGrouped().stream()
            .collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> ((Number) row[1]).longValue(),
                (a, b) -> a
            ));

        return types.stream()
            .map(t -> new DocumentTypeDTO(
                t.getId(),
                t.getName(),
                t.getDescription(),
                countByTypeId.getOrDefault(t.getId(), 0L),
                t.getCreatedAt()
            ))
            .toList();
    }

    /**
     * 创建文档类型
     */
    @Transactional
    public DocumentTypeDTO create(CreateRequest request) {
        String name = normalizeName(request.name());
        if (documentTypeRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_NAME_EXISTS, "文档类型名称已存在");
        }

        DocumentTypeEntity entity = new DocumentTypeEntity();
        entity.setName(name);
        entity.setDescription(trimToNull(request.description()));
        entity = documentTypeRepository.save(entity);

        log.info("创建文档类型: id={}, name={}", entity.getId(), entity.getName());
        return new DocumentTypeDTO(entity.getId(), entity.getName(), entity.getDescription(), 0L, entity.getCreatedAt());
    }

    /**
     * 更新文档类型
     */
    @Transactional
    public DocumentTypeDTO update(Long id, UpdateRequest request) {
        DocumentTypeEntity entity = documentTypeRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND, "文档类型不存在"));

        String name = normalizeName(request.name());
        documentTypeRepository.findByName(name)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BusinessException(ErrorCode.DOCUMENT_TYPE_NAME_EXISTS, "文档类型名称已存在");
            });

        entity.setName(name);
        entity.setDescription(trimToNull(request.description()));
        entity = documentTypeRepository.save(entity);

        long count = knowledgeBaseRepository.countByDocumentTypeId(id);
        log.info("更新文档类型: id={}, name={}", entity.getId(), entity.getName());
        return new DocumentTypeDTO(entity.getId(), entity.getName(), entity.getDescription(), count, entity.getCreatedAt());
    }

    /**
     * 删除文档类型（被文档引用时禁止删除）
     */
    @Transactional
    public void delete(Long id) {
        DocumentTypeEntity entity = documentTypeRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND, "文档类型不存在"));

        long count = knowledgeBaseRepository.countByDocumentTypeId(id);
        if (count > 0) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_IN_USE, "该文档类型下仍有文档，无法删除");
        }

        documentTypeRepository.delete(entity);
        log.info("删除文档类型: id={}, name={}", id, entity.getName());
    }

    /**
     * 校验文档类型存在，返回实体（供上传时校验）
     */
    public DocumentTypeEntity getEntity(Long id) {
        return documentTypeRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND, "文档类型不存在"));
    }

    /**
     * 将「显式选中的文档ID」与「文档类型ID对应的文档ID」取并集，返回去重后的知识库ID列表。
     */
    @Transactional(readOnly = true)
    public List<Long> resolveKnowledgeBaseIds(List<Long> knowledgeBaseIds, List<Long> documentTypeIds) {
        Set<Long> ids = new LinkedHashSet<>();
        if (knowledgeBaseIds != null) {
            ids.addAll(knowledgeBaseIds);
        }
        if (documentTypeIds != null && !documentTypeIds.isEmpty()) {
            List<Long> typeKbIds = knowledgeBaseRepository
                .findByDocumentTypeIdInOrderByUploadedAtDesc(documentTypeIds).stream()
                .map(KnowledgeBaseEntity::getId)
                .toList();
            ids.addAll(typeKbIds);
        }
        return new ArrayList<>(ids);
    }

    /**
     * 解析知识库ID；解析结果为空时抛出业务异常。
     * 区分「未选择任何范围」与「所选文档类型下没有文档」两种提示。
     */
    @Transactional(readOnly = true)
    public List<Long> resolveKnowledgeBaseIdsOrThrow(List<Long> knowledgeBaseIds, List<Long> documentTypeIds) {
        List<Long> resolvedIds = resolveKnowledgeBaseIds(knowledgeBaseIds, documentTypeIds);
        if (!resolvedIds.isEmpty()) {
            return resolvedIds;
        }

        boolean hasDocumentType = documentTypeIds != null && !documentTypeIds.isEmpty();
        if (hasDocumentType) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选文档类型下暂无文档，请先上传对应类型的文档");
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "至少选择一个知识库或文档类型");
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "类型名称不能为空");
        }
        return name.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
