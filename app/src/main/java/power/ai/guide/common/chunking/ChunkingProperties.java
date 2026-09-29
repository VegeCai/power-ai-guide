package power.ai.guide.common.chunking;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 层级切块配置
 * <p>
 * 对应 application.yml 中的 app.ai.rag.chunking。
 * 阈值按「字符」粒度实现（中文约 1 字 ≈ 1 token）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai.rag.chunking")
public class ChunkingProperties {

  /**
   * 是否启用层级切块；关闭时回退到 Spring AI 默认 TokenTextSplitter（扁平切块）。
   */
  private boolean enabled = true;

  /**
   * 单个块的最小长度（字符），小于该长度的尾块会并入前一块。
   */
  private int minChunkSize = 128;

  /**
   * 单个块的最大长度（字符），超过后按句子/段落边界切分。
   */
  private int maxChunkSize = 512;

  /**
   * 相邻块之间的重叠比例（0~1），取 0.25。
   */
  private double overlapRatio = 0.25;

  /**
   * 标题行最大长度（字符），超过则视为正文，防止误判长句为标题。
   */
  private int maxHeadingLength = 50;
}
