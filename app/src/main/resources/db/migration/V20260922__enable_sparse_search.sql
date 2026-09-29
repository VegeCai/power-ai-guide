DO $$
BEGIN
  -- 1) 稀疏检索依赖的 pg_trgm 扩展（字符三元组，近似「稀疏-稠密多路召回」的 BM25 词面匹配路）
  IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
    CREATE EXTENSION pg_trgm WITH SCHEMA public;
  END IF;

  -- GIN 索引：加速 trigram 匹配。
  -- 当前稀疏查询直接使用 word_similarity(?, content) 计算分数以保证召回，
  -- 该索引留作后续 <% / %> 预过滤（降低 word_similarity_threshold 后）的性能优化。
  IF to_regclass('public.vector_store_content_trgm_idx') IS NULL THEN
    CREATE INDEX vector_store_content_trgm_idx
      ON vector_store USING gin (content gin_trgm_ops);
  END IF;

  -- 2) 存储流式回答的命中来源（供会话详情回查；json 类型与 vector_store.metadata 一致）
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'rag_chat_messages' AND column_name = 'sources'
  ) THEN
    ALTER TABLE rag_chat_messages ADD COLUMN sources JSON;
  END IF;
END
$$;
