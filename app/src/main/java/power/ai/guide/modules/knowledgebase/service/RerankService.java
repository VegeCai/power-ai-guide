package power.ai.guide.modules.knowledgebase.service;

import power.ai.guide.common.ai.ApiPathResolver;
import power.ai.guide.common.ai.LlmProviderRegistry;
import power.ai.guide.common.ai.RerankApiStyle;
import power.ai.guide.modules.knowledgebase.service.KnowledgeBaseQueryProperties.Rerank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交叉编码器重排序服务
 * 使用独立配置的默认排序 Provider（baseUrl/apiKey/rerankModel/rerankUrl/apiStyle），
 * 调用 OpenAI 兼容的 {@code /rerank} 或 DashScope 原生 rerank 端点，将召回 Top-K 精排为 Top-N。
 */
@Slf4j
@Service
public class RerankService {

  private final LlmProviderRegistry llmProviderRegistry;
  private final Rerank properties;

  public RerankService(LlmProviderRegistry llmProviderRegistry,
      KnowledgeBaseQueryProperties queryProperties) {
    this.llmProviderRegistry = llmProviderRegistry;
    this.properties = queryProperties.getRerank();
  }

  /**
   * 对候选文档做交叉编码器重排序，返回按相关度降序的 Top-N 文档。
   * 文档的 {@link Document#getScore()} 会被更新为 rerank 的相关度分数。
   * <p>
   * 兜底：重排序禁用或调用失败时，直接截断返回融合 Top-N，保证链路可用。
   */
  public List<Document> rerank(String query, List<Document> docs) {
    int topN = Math.max(1, properties.getTopN());
    if (docs == null || docs.isEmpty()) {
      return List.of();
    }
    if (!properties.isEnabled()) {
      log.debug("重排序已禁用，直接截断融合结果: topN={}", topN);
      return docs.stream().limit(topN).toList();
    }
    if (docs.size() == 1) {
      return docs;
    }

    try {
      LlmProviderRegistry.RerankProviderConfig rerankConfig =
          llmProviderRegistry.getDefaultRerankProviderConfig();
      String url = resolveEndpoint(rerankConfig);

      HttpClientSettings settings = HttpClientSettings.defaults()
          .withConnectTimeout(Duration.ofSeconds(5))
          .withReadTimeout(Duration.ofSeconds(30));
      RestClient client = RestClient.builder()
          .defaultHeader("Authorization", "Bearer " + rerankConfig.apiKey())
          .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
          .build();

      Map<String, Object> body = buildRerankBody(rerankConfig, query, docs, topN);

      Map<String, Object> response = client.post()
          .uri(URI.create(url))
          .body(body)
          .retrieve()
          .body(Map.class);

      List<Map<String, Object>> results = toResults(response);
      if (results.isEmpty()) {
        log.warn("重排序返回空结果，使用融合 Top-K 兜底: model={}", rerankConfig.model());
        return docs.stream().limit(topN).toList();
      }

      List<Scored> scored = results.stream()
          .map(r -> new Scored(toInt(r.get("index")), toDouble(r.get("relevance_score"))))
          .filter(s -> s.index() >= 0 && s.index() < docs.size())
          .sorted(Comparator.comparingDouble(Scored::score).reversed())
          .toList();

      List<Document> reranked = new ArrayList<>();
      for (Scored s : scored) {
        Document doc = docs.get(s.index());
        reranked.add(doc.mutate().score(s.score()).build());
      }
      log.info("重排序完成: 召回 {} 条 -> 精排 {} 条", docs.size(), reranked.size());
      return reranked.stream().limit(topN).toList();
    } catch (Exception e) {
      log.warn("重排序调用失败，使用融合 Top-K 兜底: {}", e.getMessage());
      return docs.stream().limit(topN).toList();
    }
  }

  /**
   * 解析 Rerank 调用端点：配置了 rerankUrl（DashScope 原生端点）则直接使用，
   * 否则按 OpenAI 兼容路径回退为 {@code {baseUrl}/rerank}。
   */
  private String resolveEndpoint(LlmProviderRegistry.RerankProviderConfig config) {
    if (config.rerankUrl() != null && !config.rerankUrl().isBlank()) {
      return ApiPathResolver.stripTrailingSlashes(config.rerankUrl());
    }
    return ApiPathResolver.stripTrailingSlashes(config.baseUrl()) + "/rerank";
  }

  /**
   * 按 Provider 的 Rerank API 风格构造请求体。
   * DashScope 原生格式使用 {@code input{query,documents}} + {@code parameters{top_n}}，
   * OpenAI 兼容格式使用顶层 {@code query/documents/top_n}。
   */
  private Map<String, Object> buildRerankBody(LlmProviderRegistry.RerankProviderConfig config,
      String query, List<Document> docs, int topN) {
    int limit = Math.min(topN, docs.size());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", config.model());
    if (config.apiStyle() == RerankApiStyle.DASHSCOPE) {
      Map<String, Object> input = new LinkedHashMap<>();
      input.put("query", query);
      input.put("documents", docs.stream().map(Document::getText).toList());
      body.put("input", input);

      Map<String, Object> parameters = new LinkedHashMap<>();
      // 不传top_n参数，则返回全部文档
      parameters.put("top_n", limit);
      parameters.put("return_documents", false);
      body.put("parameters", parameters);
    } else {
      body.put("query", query);
      body.put("documents", docs.stream().map(Document::getText).toList());
      body.put("top_n", limit);
    }
    return body;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> toResults(Map<String, Object> response) {
    Object results = response.get("results");
    if (results instanceof List<?> list) {
      return toMapList(list);
    }
    // 兼容部分厂商将结果包在 output.results 下的格式
    if (response.get("output") instanceof Map<?, ?> output
        && output.get("results") instanceof List<?> list) {
      return toMapList(list);
    }
    return List.of();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> toMapList(List<?> list) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        out.add((Map<String, Object>) map);
      }
    }
    return out;
  }

  private int toInt(Object value) {
    if (value instanceof Number n) {
      return n.intValue();
    }
    if (value instanceof String s) {
      try {
        return Integer.parseInt(s.trim());
      } catch (NumberFormatException ignored) {
        // fallthrough
      }
    }
    return -1;
  }

  private double toDouble(Object value) {
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    if (value instanceof String s) {
      try {
        return Double.parseDouble(s.trim());
      } catch (NumberFormatException ignored) {
        // fallthrough
      }
    }
    return 0.0;
  }

  private record Scored(int index, double score) {
  }
}
