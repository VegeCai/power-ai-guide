package power.ai.guide.modules.knowledgebase.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("KnowledgeBaseVectorService 稀疏-稠密融合")
class KnowledgeBaseVectorServiceTest {

  private Document doc(String id, double score) {
    return Document.builder().id(id).text("content-" + id).score(score).build();
  }

  private Map<String, Object> row(String id, double score) {
    return Map.of(
        "id", id,
        "content", "content-" + id,
        "kb_id", "1",
        "section_path", "第1章",
        "score", score
    );
  }

  @Test
  @DisplayName("α=0.3 线性融合：S = 0.7·S_dense + 0.3·S_sparse，去重后按融合分降序")
  void fuseLinearWeightedDedupSorted() {
    List<Document> dense = List.of(doc("a", 0.8), doc("b", 0.6));
    List<Map<String, Object>> sparse = List.of(row("a", 0.9), row("c", 0.5));

    List<Document> fused = KnowledgeBaseVectorService.fuse(dense, sparse, 0.3);

    assertThat(fused).extracting(Document::getId).containsExactly("a", "b", "c");
    // a = 0.7*0.8 + 0.3*0.9 = 0.83；b = 0.42；c = 0.15
    assertThat(fused.get(0).getScore()).isCloseTo(0.83, within(1e-9));
    assertThat(fused.get(1).getScore()).isCloseTo(0.42, within(1e-9));
    assertThat(fused.get(2).getScore()).isCloseTo(0.15, within(1e-9));
  }

  @Test
  @DisplayName("融合分驱动排序：稀疏高分可反超稠密高分")
  void fusedScoreDrivesOrdering() {
    List<Document> dense = List.of(doc("low", 0.5), doc("high", 0.9));
    List<Map<String, Object>> sparse = List.of(row("low", 0.99));

    List<Document> fused = KnowledgeBaseVectorService.fuse(dense, sparse, 0.3);

    // low = 0.7*0.5 + 0.3*0.99 = 0.647 > high = 0.7*0.9 = 0.63
    assertThat(fused).extracting(Document::getId).containsExactly("low", "high");
  }

  @Test
  @DisplayName("同一 chunk 两路都命中时保留稠密 Document 对象，仅融合分数")
  void fuseKeepsDenseDocumentOnOverlap() {
    List<Document> dense = List.of(doc("a", 0.8));
    List<Map<String, Object>> sparse = List.of(row("a", 0.9));

    List<Document> fused = KnowledgeBaseVectorService.fuse(dense, sparse, 0.3);

    assertThat(fused).hasSize(1);
    assertThat(fused.get(0).getText()).isEqualTo("content-a");
    assertThat(fused.get(0).getScore()).isCloseTo(0.83, within(1e-9));
  }

  @Test
  @DisplayName("toSimilarity：优先 score，其次 1 - distance，否则 0")
  void toSimilarityPrecedence() {
    assertThat(KnowledgeBaseVectorService.toSimilarity(doc("a", 0.9))).isEqualTo(0.9);

    Document withDistance = Document.builder().id("b").text("x")
        .metadata(Map.of("distance", 0.2)).build();
    assertThat(KnowledgeBaseVectorService.toSimilarity(withDistance)).isCloseTo(0.8, within(1e-9));

    Document empty = Document.builder().id("c").text("x").build();
    assertThat(KnowledgeBaseVectorService.toSimilarity(empty)).isEqualTo(0.0);
  }

  @Test
  @DisplayName("toDocument：稀疏行映射为带元数据的 Document")
  void toDocumentFromSparseRow() {
    Map<String, Object> row = Map.of(
        "id", "x", "content", "正文", "kb_id", "5", "section_path", "第1章", "score", 0.7);

    Document doc = KnowledgeBaseVectorService.toDocument(row);

    assertThat(doc.getId()).isEqualTo("x");
    assertThat(doc.getText()).isEqualTo("正文");
    assertThat(doc.getMetadata()).containsEntry("kb_id", "5").containsEntry("section_path", "第1章");
    assertThat(doc.getScore()).isEqualTo(0.7);
  }
}
