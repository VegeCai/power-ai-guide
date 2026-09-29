-- Rerank 端点与请求风格配置：
--   1) llm_provider_config 增加 rerank_url（完整端点）与 rerank_api_style（openai-compatible / dashscope）；
--   2) 修正内置 dashscope 的排序模型名与端点（DashScope 原生 rerank 端点，非 OpenAI 兼容模式）。

ALTER TABLE llm_provider_config ADD COLUMN rerank_url VARCHAR(512);
ALTER TABLE llm_provider_config ADD COLUMN rerank_api_style VARCHAR(32);

-- 模型名统一为 DashScope 当前可用的 qwen3.7-text-rerank（旧值 qwen3-rerank 可能已下线）。
UPDATE llm_provider_config
SET rerank_model = 'qwen3.7-text-rerank'
WHERE id = 'dashscope'
  AND rerank_model = 'qwen3-rerank';

-- 为已有 dashscope 回填原生 rerank 端点与请求风格。
UPDATE llm_provider_config
SET rerank_url = 'https://ws-gcs0g5tktrb0ockx.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank',
    rerank_api_style = 'dashscope'
WHERE id = 'dashscope'
  AND (rerank_url IS NULL OR rerank_url = '');
