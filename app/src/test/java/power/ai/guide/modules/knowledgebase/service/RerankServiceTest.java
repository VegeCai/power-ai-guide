package power.ai.guide.modules.knowledgebase.service;

import com.sun.net.httpserver.HttpServer;
import power.ai.guide.common.ai.LlmProviderRegistry;
import power.ai.guide.common.ai.RerankApiStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RerankService 交叉编码器重排序")
class RerankServiceTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  private Document doc(String id, String text) {
    return Document.builder().id(id).text(text).build();
  }

  @Test
  @DisplayName("解析 /rerank 响应，按 relevance_score 降序重排并回写分数")
  void rerankParsesAndReorders() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/rerank", exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String json = "{\"results\":[{\"index\":1,\"relevance_score\":0.95},{\"index\":0,\"relevance_score\":0.60}]}";
      byte[] resp = json.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, resp.length);
      exchange.getResponseBody().write(resp);
      exchange.close();
    });
    server.start();
    int port = server.getAddress().getPort();

    LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
    when(registry.getDefaultRerankProviderConfig()).thenReturn(
        new LlmProviderRegistry.RerankProviderConfig("http://localhost:" + port, "test-key",
            "qwen3-rerank", null, RerankApiStyle.OPENAI_COMPATIBLE));
    RerankService service = new RerankService(registry, new KnowledgeBaseQueryProperties());

    List<Document> result = service.rerank("查询词", List.of(doc("a", "条款A"), doc("b", "条款B")));

    assertThat(result).extracting(Document::getId).containsExactly("b", "a");
    assertThat(result.get(0).getScore()).isEqualTo(0.95);
    assertThat(result.get(1).getScore()).isEqualTo(0.60);

    // 校验请求体：模型、查询词、文档列表、top_n 均正确下发
    assertThat(requestBody.get())
        .contains("\"model\":\"qwen3-rerank\"")
        .contains("\"query\":\"查询词\"")
        .contains("\"条款A\"")
        .contains("\"条款B\"")
        .contains("\"top_n\":2");
  }

  @Test
  @DisplayName("DashScope 原生格式：input/parameters 请求体 + output.results 响应")
  void rerankDashScopeFormat() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/dashscope-rerank", exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String json = "{\"output\":{\"results\":[{\"index\":0,\"relevance_score\":0.70},"
          + "{\"index\":1,\"relevance_score\":0.90}]}}";
      byte[] resp = json.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, resp.length);
      exchange.getResponseBody().write(resp);
      exchange.close();
    });
    server.start();
    int port = server.getAddress().getPort();

    LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
    when(registry.getDefaultRerankProviderConfig()).thenReturn(
        new LlmProviderRegistry.RerankProviderConfig("http://localhost:" + port, "test-key",
            "qwen3.7-text-rerank", "http://localhost:" + port + "/dashscope-rerank",
            RerankApiStyle.DASHSCOPE));
    RerankService service = new RerankService(registry, new KnowledgeBaseQueryProperties());

    List<Document> result = service.rerank("查询词", List.of(doc("a", "条款A"), doc("b", "条款B")));

    assertThat(result).extracting(Document::getId).containsExactly("b", "a");
    assertThat(result.get(0).getScore()).isEqualTo(0.90);

    // 校验请求体：input/parameters 嵌套结构、top_n、返回文档开关均正确下发
    assertThat(requestBody.get())
        .contains("\"model\":\"qwen3.7-text-rerank\"")
        .contains("\"input\"")
        .contains("\"query\":\"查询词\"")
        .contains("\"parameters\"")
        .contains("\"top_n\":2")
        .contains("\"return_documents\":false");
  }

  @Test
  @DisplayName("重排序禁用时直接截断融合 Top-N")
  void rerankDisabledTruncates() {
    KnowledgeBaseQueryProperties props = new KnowledgeBaseQueryProperties();
    props.getRerank().setEnabled(false);
    RerankService service = new RerankService(mock(LlmProviderRegistry.class), props);

    List<Document> result = service.rerank("q",
        List.of(doc("a", "x"), doc("b", "y"), doc("c", "z")));

    assertThat(result).extracting(Document::getId).containsExactly("a", "b", "c");
  }

  @Test
  @DisplayName("空输入与单条输入走透传")
  void rerankPassthroughEmptyAndSingle() {
    RerankService service = new RerankService(mock(LlmProviderRegistry.class),
        new KnowledgeBaseQueryProperties());

    assertThat(service.rerank("q", List.of())).isEmpty();

    Document single = doc("a", "x");
    List<Document> result = service.rerank("q", List.of(single));
    assertThat(result).containsExactly(single);
  }

  @Test
  @DisplayName("调用失败时回退为融合 Top-K（保持原顺序，不中断链路）")
  void rerankFallsBackOnError() {
    LlmProviderRegistry registry = mock(LlmProviderRegistry.class);
    when(registry.getDefaultRerankProviderConfig()).thenReturn(
        new LlmProviderRegistry.RerankProviderConfig("http://localhost:1", "test-key",
            "qwen3-rerank", null, RerankApiStyle.OPENAI_COMPATIBLE));
    RerankService service = new RerankService(registry, new KnowledgeBaseQueryProperties());

    List<Document> docs = List.of(doc("a", "x"), doc("b", "y"), doc("c", "z"));
    List<Document> result = service.rerank("q", docs);

    assertThat(result).extracting(Document::getId).containsExactly("a", "b", "c");
  }
}
