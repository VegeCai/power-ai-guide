package power.ai.guide.common.chunking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 层级切块器：基于标题树做自适应语义切块，并为每个块标注层级路径。
 * <p>
 * 产物 {@link Document}：
 * <ul>
 *   <li>text = {@code "[路径 > 标题] " + 块文本}（路径随内容一起嵌入，检索时自带上下文）；</li>
 *   <li>metadata 携带 section_path / heading / heading_level / parent_id / chunk_index。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HierarchicalChunker {

  public static final String METADATA_SECTION_PATH = "section_path";
  public static final String METADATA_HEADING = "heading";
  public static final String METADATA_HEADING_LEVEL = "heading_level";
  public static final String METADATA_PARENT_ID = "parent_id";
  public static final String METADATA_CHUNK_INDEX = "chunk_index";

  private static final String SENTENCE_BREAKS = "。！？；.!?;";

  private final HeadingDetector headingDetector;
  private final ChunkingProperties properties;

  /**
   * 将整篇文本切分为带层级元数据的块。
   *
   * @param content 文档文本
   * @return 切分后的块列表
   */
  public List<Document> chunk(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    List<Section> sections = headingDetector.detect(content, properties.getMaxHeadingLength());
    List<Document> documents = new ArrayList<>();
    for (Section section : sections) {
      String parentId = UUID.randomUUID().toString();
      List<String> pieces = splitSection(section.getContent());
      for (int i = 0; i < pieces.size(); i++) {
        documents.add(toDocument(section, parentId, i, pieces.get(i)));
      }
    }
    log.debug("层级切块完成: sections={}, chunks={}", sections.size(), documents.size());
    return documents;
  }

  private Document toDocument(Section section, String parentId, int index, String piece) {
    String annotated = section.getPath().isBlank()
        ? piece
        : "[" + section.getPath() + "] " + piece;
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(METADATA_SECTION_PATH, section.getPath());
    metadata.put(METADATA_HEADING, section.getTitle());
    metadata.put(METADATA_HEADING_LEVEL, section.getLevel());
    metadata.put(METADATA_PARENT_ID, parentId);
    metadata.put(METADATA_CHUNK_INDEX, index);
    return new Document(annotated, metadata);
  }

  /**
   * 对单个标题节点下的直接正文做自适应切块：min/max 约束 + 重叠 + 句子/段落边界对齐。
   */
  List<String> splitSection(String content) {
    int min = Math.max(1, properties.getMinChunkSize());
    int max = Math.max(min, properties.getMaxChunkSize());
    int overlap = (int) Math.round(max * properties.getOverlapRatio());

    String text = content == null ? "" : content.strip();
    if (text.isEmpty()) {
      return List.of();
    }
    if (text.length() <= max) {
      return List.of(text);
    }

    List<Integer> breaks = breakPoints(text);
    List<int[]> ranges = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int remaining = text.length() - start;
      if (remaining <= max) {
        ranges.add(new int[] { start, text.length() });
        break;
      }
      int end = Math.min(start + max, text.length());
      int cut = findBreak(breaks, start + min, end);
      if (cut != -1) {
        end = cut;
      }
      ranges.add(new int[] { start, end });
      int next = end - overlap;
      if (next <= start) {
        next = start + 1;
      }
      start = next;
    }

    mergeTinyTail(ranges, min);
    List<String> chunks = new ArrayList<>();
    for (int[] range : ranges) {
      chunks.add(text.substring(range[0], range[1]));
    }
    return chunks;
  }

  private void mergeTinyTail(List<int[]> ranges, int min) {
    while (ranges.size() >= 2) {
      int[] last = ranges.get(ranges.size() - 1);
      if (last[1] - last[0] >= min) {
        break;
      }
      int[] prev = ranges.get(ranges.size() - 2);
      ranges.set(ranges.size() - 2, new int[] { prev[0], last[1] });
      ranges.remove(ranges.size() - 1);
    }
  }

  private List<Integer> breakPoints(String text) {
    TreeSet<Integer> set = new TreeSet<>();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n' || SENTENCE_BREAKS.indexOf(c) >= 0) {
        set.add(i + 1);
      }
    }
    return new ArrayList<>(set);
  }

  private int findBreak(List<Integer> breaks, int from, int to) {
    for (int i = breaks.size() - 1; i >= 0; i--) {
      int b = breaks.get(i);
      if (b <= to && b > from) {
        return b;
      }
      if (b <= from) {
        break;
      }
    }
    return -1;
  }
}
