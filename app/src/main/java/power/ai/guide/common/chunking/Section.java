package power.ai.guide.common.chunking;

/**
 * 标题树中的一个内容片段：某个标题节点及其直接正文（不含子标题内容）。
 * <p>
 * {@link #path} 是从根到该标题的累积路径（用 " &gt; " 连接），用于层级路径标注。
 */
public class Section {

  private final int level;
  private final String title;
  private final String path;
  private final String content;

  public Section(int level, String title, String path, String content) {
    this.level = level;
    this.title = title;
    this.path = path;
    this.content = content;
  }

  public int getLevel() {
    return level;
  }

  public String getTitle() {
    return title;
  }

  public String getPath() {
    return path;
  }

  public String getContent() {
    return content;
  }
}
