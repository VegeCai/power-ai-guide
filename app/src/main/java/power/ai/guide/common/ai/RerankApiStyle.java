package power.ai.guide.common.ai;

import java.util.Locale;

/**
 * Rerank 接口的请求风格。
 * <p>
 * 不同厂商的 Rerank 端点请求体结构差异较大，切换 Rerank 模型时需按厂商选择对应风格：
 * <ul>
 *   <li>{@link #OPENAI_COMPATIBLE}：OpenAI 兼容端点 {@code POST {baseUrl}/rerank}，
 *       请求体顶层为 {@code model/query/documents/top_n}。</li>
 *   <li>{@link #DASHSCOPE}：DashScope 原生端点（需单独配置 rerankUrl），
 *       请求体为 {@code model/input{query,documents}/parameters{top_n,return_documents}}。</li>
 * </ul>
 */
public enum RerankApiStyle {

  OPENAI_COMPATIBLE,
  DASHSCOPE;

  /**
   * 从配置值解析风格；空值回退为 {@link #OPENAI_COMPATIBLE}，未知值抛异常以尽早暴露配置错误。
   */
  public static RerankApiStyle from(String value) {
    if (value == null || value.isBlank()) {
      return OPENAI_COMPATIBLE;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (normalized) {
      case "openai", "openai_compatible" -> OPENAI_COMPATIBLE;
      case "dashscope", "dashscope_native" -> DASHSCOPE;
      default -> throw new IllegalArgumentException("未知的 rerank-api-style: " + value);
    };
  }
}
