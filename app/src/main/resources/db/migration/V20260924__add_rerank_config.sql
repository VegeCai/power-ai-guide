-- 排序（Rerank）模型独立配置：
--   1) llm_provider_config 增加 rerank_model / supports_rerank；
--   2) llm_global_setting 增加默认排序 Provider。

ALTER TABLE llm_provider_config ADD COLUMN rerank_model VARCHAR(128);
ALTER TABLE llm_provider_config ADD COLUMN supports_rerank BOOLEAN NOT NULL DEFAULT false;

-- 为内置 dashscope 回填排序模型，保持既有默认行为（排序走 dashscope 的 qwen3-rerank）。
UPDATE llm_provider_config
SET supports_rerank = true,
    rerank_model = 'qwen3-rerank'
WHERE id = 'dashscope'
  AND (rerank_model IS NULL OR rerank_model = '');

-- 默认排序 Provider 先回填为默认聊天 Provider，再收紧为 NOT NULL。
ALTER TABLE llm_global_setting ADD COLUMN default_rerank_provider_id VARCHAR(64);
UPDATE llm_global_setting
SET default_rerank_provider_id = default_chat_provider_id
WHERE default_rerank_provider_id IS NULL OR default_rerank_provider_id = '';
ALTER TABLE llm_global_setting ALTER COLUMN default_rerank_provider_id SET NOT NULL;
