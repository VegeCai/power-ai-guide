package power.ai.guide.common.chunking;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标题识别器：把 Tika 解析出的扁平文本按行扫描，重建标题层级（正则 + 启发式）。
 * <p>
 * 识别规则（行首、整行、长度受限）：
 * <ul>
 *   <li>Markdown：{@code #} ~ {@code ######} 标题；</li>
 *   <li>规程编号：{@code 第X章/节/条/编/部分}、{@code X.Y.Z}、{@code 一、}、{@code （一）}。</li>
 * </ul>
 * 未命中任何标题时退化为单一内容片段（等价于旧的扁平切块）。
 */
@Component
public class HeadingDetector {

  private static final Pattern MARKDOWN = Pattern.compile("^(#{1,6})\\s+(.+)$");
  private static final Pattern CHAPTER =
      Pattern.compile("^第[一二三四五六七八九十百千0-9]+([章节条编部分])[\\s、.．:：]+(.+)$");
  private static final Pattern DOTTED_NUM =
      Pattern.compile("^(\\d+(?:\\.\\d+)*)(?:\\s*[、.．]\\s*|\\s+)(.+)$");
  private static final Pattern CN_NUM = Pattern.compile("^[一二三四五六七八九十]+[、.．]\\s*(.+)$");
  private static final Pattern CN_PAREN = Pattern.compile("^[（(][一二三四五六七八九十]+[）)]\\s*(.+)$");

  /**
   * 将扁平文本重建为标题层级片段列表。
   *
   * @param text              扁平文本
   * @param maxHeadingLength  标题行最大长度（超过视为正文）
   * @return 内容片段列表（顺序保持原文顺序）
   */
  public List<Section> detect(String text, int maxHeadingLength) {
    List<Section> sections = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return sections;
    }

    Deque<Heading> stack = new ArrayDeque<>();
    StringBuilder buffer = new StringBuilder();

    for (String rawLine : text.split("\\R", -1)) {
      String line = rawLine.strip();
      if (line.isEmpty()) {
        buffer.append('\n');
        continue;
      }
      Optional<Heading> heading = match(line, maxHeadingLength);
      if (heading.isPresent()) {
        flush(sections, stack, buffer);
        Heading h = heading.get();
        while (!stack.isEmpty() && stack.peek().level() >= h.level()) {
          stack.pop();
        }
        stack.push(h);
      } else {
        buffer.append(line).append('\n');
      }
    }
    flush(sections, stack, buffer);
    return sections;
  }

  private void flush(List<Section> sections, Deque<Heading> stack, StringBuilder buffer) {
    String content = buffer.toString().strip();
    buffer.setLength(0);
    if (content.isEmpty()) {
      return;
    }
    // descendingIterator 从栈底（根标题）到栈顶（叶子标题），拼出「根 > 子 > 孙」路径
    List<String> titles = new ArrayList<>();
    stack.descendingIterator().forEachRemaining(h -> titles.add(h.title()));
    int level = stack.isEmpty() ? 0 : stack.peek().level();
    String title = stack.isEmpty() ? "" : stack.peek().title();
    sections.add(new Section(level, title, String.join(" > ", titles), content));
  }

  private Optional<Heading> match(String line, int maxHeadingLength) {
    Matcher m = MARKDOWN.matcher(line);
    if (m.matches()) {
      return toHeading(m.group(1).length(), m.group(2), maxHeadingLength);
    }

    m = CHAPTER.matcher(line);
    if (m.matches()) {
      int level = switch (m.group(1)) {
        case "编", "部分" -> 1;
        case "节" -> 2;
        case "条" -> 3;
        default -> 1; // 章
      };
      return toHeading(level, line, maxHeadingLength);
    }

    m = CN_PAREN.matcher(line);
    if (m.matches()) {
      return toHeading(3, line, maxHeadingLength);
    }

    m = CN_NUM.matcher(line);
    if (m.matches()) {
      return toHeading(2, line, maxHeadingLength);
    }

    m = DOTTED_NUM.matcher(line);
    if (m.matches()) {
      return toHeading(m.group(1).split("\\.").length, line, maxHeadingLength);
    }

    return Optional.empty();
  }

  private Optional<Heading> toHeading(int level, String title, int maxHeadingLength) {
    String t = title == null ? "" : title.strip();
    if (t.isBlank() || t.length() > maxHeadingLength) {
      return Optional.empty();
    }
    return Optional.of(new Heading(level, t));
  }

  private record Heading(int level, String title) {
  }
}
