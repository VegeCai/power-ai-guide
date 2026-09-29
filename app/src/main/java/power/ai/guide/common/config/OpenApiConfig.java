package power.ai.guide.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("电力 AI 助手 API")
                        .description("面向电力行业的知识库管理、RAG 智能问答 RESTful API 文档")
                        .version("1.0.0"));
    }
}
