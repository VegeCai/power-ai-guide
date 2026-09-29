package power.ai.guide.common.json;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 轻量 JSON 编解码工具（Jackson 3）。
 * 仅用于内部字段（如 rag_chat_messages.sources）的序列化 / 反序列化，
 * 不参与 Spring MVC 的请求响应转换。
 */
public final class JsonSupport {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private JsonSupport() {}

  /**
   * 对象序列化为 JSON 字符串。
   */
  public static String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("JSON 序列化失败", e);
    }
  }

  /**
   * JSON 数组反序列化为 List。
   * 空串或 null 返回空列表。
   */
  public static <T> List<T> toList(String json, Class<T> elementType) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(json,
          MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
    } catch (Exception e) {
      throw new IllegalStateException("JSON 反序列化失败", e);
    }
  }
}
