package power.ai.guide.modules.knowledgebase.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import power.ai.guide.common.exception.BusinessException;
import power.ai.guide.common.exception.ErrorCode;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeDTO;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeEntity;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeRequest.CreateRequest;
import power.ai.guide.modules.knowledgebase.model.DocumentTypeRequest.UpdateRequest;
import power.ai.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import power.ai.guide.modules.knowledgebase.repository.DocumentTypeRepository;
import power.ai.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DocumentTypeService 文档类型管理")
@ExtendWith(MockitoExtension.class)
class DocumentTypeServiceTest {

  @Mock
  private DocumentTypeRepository documentTypeRepository;

  @Mock
  private KnowledgeBaseRepository knowledgeBaseRepository;

  @InjectMocks
  private DocumentTypeService documentTypeService;

  private DocumentTypeEntity entity(Long id, String name) {
    DocumentTypeEntity e = new DocumentTypeEntity();
    e.setId(id);
    e.setName(name);
    e.setDescription(null);
    e.setCreatedAt(LocalDateTime.of(2026, 9, 26, 10, 0));
    return e;
  }

  @Test
  @DisplayName("create：名称去空格、描述去空格、文档数为 0")
  void createTrimsNameAndDescription() {
    when(documentTypeRepository.existsByName("规程")).thenReturn(false);
    when(documentTypeRepository.save(any(DocumentTypeEntity.class))).thenAnswer(inv -> {
      DocumentTypeEntity e = inv.getArgument(0);
      e.setId(1L);
      e.setCreatedAt(LocalDateTime.of(2026, 9, 26, 10, 0));
      return e;
    });

    DocumentTypeDTO dto = documentTypeService.create(new CreateRequest("  规程  ", "  电力规程说明  "));

    assertThat(dto.id()).isEqualTo(1L);
    assertThat(dto.name()).isEqualTo("规程");
    assertThat(dto.description()).isEqualTo("电力规程说明");
    assertThat(dto.documentCount()).isZero();
    verify(documentTypeRepository).save(any(DocumentTypeEntity.class));
  }

  @Test
  @DisplayName("create：名称已存在时抛 DOCUMENT_TYPE_NAME_EXISTS")
  void createRejectsDuplicateName() {
    when(documentTypeRepository.existsByName("规程")).thenReturn(true);

    assertThatThrownBy(() -> documentTypeService.create(new CreateRequest("规程", null)))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getCode()).isEqualTo(ErrorCode.DOCUMENT_TYPE_NAME_EXISTS.getCode()));

    verify(documentTypeRepository, never()).save(any(DocumentTypeEntity.class));
  }

  @Test
  @DisplayName("create：名称为空时抛 BAD_REQUEST")
  void createRejectsBlankName() {
    assertThatThrownBy(() -> documentTypeService.create(new CreateRequest("   ", null)))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
  }

  @Test
  @DisplayName("update：不存在时抛 DOCUMENT_TYPE_NOT_FOUND")
  void updateRejectsMissingType() {
    when(documentTypeRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> documentTypeService.update(99L, new UpdateRequest("规程", null)))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getCode()).isEqualTo(ErrorCode.DOCUMENT_TYPE_NOT_FOUND.getCode()));
  }

  @Test
  @DisplayName("update：改名撞上其它类型时抛 DOCUMENT_TYPE_NAME_EXISTS")
  void updateRejectsNameConflict() {
    when(documentTypeRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "旧名")));
    when(documentTypeRepository.findByName("规程")).thenReturn(Optional.of(entity(2L, "规程")));

    assertThatThrownBy(() -> documentTypeService.update(1L, new UpdateRequest("规程", null)))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getCode()).isEqualTo(ErrorCode.DOCUMENT_TYPE_NAME_EXISTS.getCode()));

    verify(documentTypeRepository, never()).save(any(DocumentTypeEntity.class));
  }

  @Test
  @DisplayName("delete：无文档引用时正常删除")
  void deleteSucceedsWhenNoDocument() {
    when(documentTypeRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "规程")));
    when(knowledgeBaseRepository.countByDocumentTypeId(1L)).thenReturn(0L);

    documentTypeService.delete(1L);

    verify(documentTypeRepository).delete(any(DocumentTypeEntity.class));
  }

  @Test
  @DisplayName("delete：仍有文档引用时抛 DOCUMENT_TYPE_IN_USE（禁止删除）")
  void deleteBlockedWhenInUse() {
    when(documentTypeRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "规程")));
    when(knowledgeBaseRepository.countByDocumentTypeId(1L)).thenReturn(3L);

    assertThatThrownBy(() -> documentTypeService.delete(1L))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getCode()).isEqualTo(ErrorCode.DOCUMENT_TYPE_IN_USE.getCode()));

    verify(documentTypeRepository, never()).delete(any(DocumentTypeEntity.class));
  }

  @Test
  @DisplayName("resolveKnowledgeBaseIds：显式文档与类型下文档取并集并去重")
  void resolveKnowledgeBaseIdsUnionDedup() {
    KnowledgeBaseEntity kb10 = mock(KnowledgeBaseEntity.class);
    KnowledgeBaseEntity kb20 = mock(KnowledgeBaseEntity.class);
    when(kb10.getId()).thenReturn(10L);
    when(kb20.getId()).thenReturn(20L);

    when(knowledgeBaseRepository.findByDocumentTypeIdInOrderByUploadedAtDesc(List.of(1L, 2L)))
        .thenReturn(List.of(kb10, kb20));

    // 显式选了 10、30，类型下解析出 10、20，合并后应为 10、30、20（去重，保持顺序）
    List<Long> resolved = documentTypeService.resolveKnowledgeBaseIds(
        List.of(10L, 30L), List.of(1L, 2L));

    assertThat(resolved).containsExactly(10L, 30L, 20L);
  }

  @Test
  @DisplayName("resolveKnowledgeBaseIds：仅选类型时返回该类型下全部文档")
  void resolveKnowledgeBaseIdsByTypeOnly() {
    KnowledgeBaseEntity kb = mock(KnowledgeBaseEntity.class);
    when(kb.getId()).thenReturn(7L);
    when(knowledgeBaseRepository.findByDocumentTypeIdInOrderByUploadedAtDesc(List.of(1L)))
        .thenReturn(List.of(kb));

    assertThat(documentTypeService.resolveKnowledgeBaseIds(List.of(), List.of(1L)))
        .containsExactly(7L);
  }

  @Test
  @DisplayName("resolveKnowledgeBaseIds：入参为 null 时按空集合处理")
  void resolveKnowledgeBaseIdsToleratesNull() {
    assertThat(documentTypeService.resolveKnowledgeBaseIds(null, null)).isEmpty();
    assertThat(documentTypeService.resolveKnowledgeBaseIds(null, List.of())).isEmpty();
    verify(knowledgeBaseRepository, never()).findByDocumentTypeIdInOrderByUploadedAtDesc(any());
    verify(knowledgeBaseRepository, never()).findByDocumentTypeIdOrderByUploadedAtDesc(anyLong());
  }
}
