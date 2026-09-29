package power.ai.guide.common.chunking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HierarchicalChunker 层级切块")
class HierarchicalChunkerTest {

  private HierarchicalChunker newChunker(int min, int max, double overlap) {
    ChunkingProperties props = new ChunkingProperties();
    props.setMinChunkSize(min);
    props.setMaxChunkSize(max);
    props.setOverlapRatio(overlap);
    props.setMaxHeadingLength(50);
    return new HierarchicalChunker(new HeadingDetector(), props);
  }

  @Test
  @DisplayName("切块标注层级路径并写入元数据")
  void annotatesPathAndMetadata() {
    HierarchicalChunker chunker = newChunker(128, 512, 0.25);
    String content = "第1章 总则\n本章说明。\n1.1 术语定义\n术语的具体含义是……。\n";

    List<Document> docs = chunker.chunk(content);

    assertThat(docs).hasSize(2);
    Document first = docs.get(0);
    assertThat(first.getText()).startsWith("[第1章 总则] ");
    assertThat(first.getMetadata())
        .containsEntry(HierarchicalChunker.METADATA_SECTION_PATH, "第1章 总则")
        .containsEntry(HierarchicalChunker.METADATA_HEADING_LEVEL, 1);

    Document second = docs.get(1);
    assertThat(second.getText()).startsWith("[第1章 总则 > 1.1 术语定义] ");
    assertThat(second.getMetadata())
        .containsEntry(HierarchicalChunker.METADATA_SECTION_PATH, "第1章 总则 > 1.1 术语定义")
        .containsEntry(HierarchicalChunker.METADATA_HEADING_LEVEL, 2);
  }

  @Test
  @DisplayName("同一标题下的块共享 parent_id，chunk_index 递增")
  void sameParentIdWithinSection() {
    HierarchicalChunker chunker = newChunker(16, 32, 0.25);
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      body.append("句子").append(i).append("，");
    }
    String content = "1.1 术语定义\n" + body + "\n";

    List<Document> docs = chunker.chunk(content);

    assertThat(docs.size()).isGreaterThan(1);
    Object parentId = docs.get(0).getMetadata().get(HierarchicalChunker.METADATA_PARENT_ID);
    assertThat(parentId).isNotNull();
    for (int i = 0; i < docs.size(); i++) {
      assertThat(docs.get(i).getMetadata().get(HierarchicalChunker.METADATA_PARENT_ID))
          .isEqualTo(parentId);
      assertThat(docs.get(i).getMetadata().get(HierarchicalChunker.METADATA_CHUNK_INDEX))
          .isEqualTo(i);
    }
  }

  @Test
  @DisplayName("长正文切块长度不超过 max")
  void chunkSizeWithinMax() {
    HierarchicalChunker chunker = newChunker(32, 64, 0.0);
    String body = "句子，".repeat(100);

    List<Document> docs = chunker.chunk(body);

    assertThat(docs.size()).isGreaterThan(1);
    for (Document doc : docs) {
      assertThat(stripPrefix(doc.getText()).length()).isLessThanOrEqualTo(64);
    }
  }

  @Test
  @DisplayName("重叠比例：相邻块存在重叠内容")
  void overlapBetweenChunks() {
    HierarchicalChunker chunker = newChunker(16, 40, 0.25);
    String body = "句子，".repeat(200);
    List<String> pieces = chunker.splitSection(body);

    assertThat(pieces.size()).isGreaterThan(1);
    int overlap = (int) Math.round(40 * 0.25);
    String first = pieces.get(0);
    assertThat(first.length()).isEqualTo(40);
    assertThat(pieces.get(1)).startsWith(first.substring(first.length() - overlap));
  }

  private String stripPrefix(String text) {
    int idx = text.indexOf("] ");
    return idx >= 0 ? text.substring(idx + 2) : text;
  }
}
