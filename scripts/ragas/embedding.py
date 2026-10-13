"""ragas 脚本共用的 Embedding 构造。

背景：项目把「聊天」和「向量」拆成了两个 Provider——
  - 聊天：dashscope        → token-plan 代理端点（纯对话，无 embedding 模型）
  - 向量：dashscop-embed   → 阿里云百炼真实端点 dashscope.aliyuncs.com，模型 text-embedding-v3
ragas 生成测试集 / 跑评测需要 embedding，应复用项目里那个「真实 embedding」端点，
而不是 token-plan（上面没有 embedding）。

因此 openai 模式默认指向真实 DashScope embedding 端点：
    RAGAS_EMBED_PROVIDER=openai
    RAGAS_EMBED_API_KEY=<dashscop-embed 的 API Key>   # 存在后端数据库里，需单独提供
    RAGAS_EMBED_BASE_URL（默认 https://dashscope.aliyuncs.com/compatible-mode/v1）
    RAGAS_EMBED_MODEL（默认 text-embedding-v3）

拿不到 key 时，也可用零依赖的「字符 n-gram 哈希」离线兜底向量先跑通流程
（RAGAS_EMBED_PROVIDER=charngram，默认）。

环境变量：
    RAGAS_EMBED_PROVIDER   openai(真实 embedding) | charngram(默认兜底)
    RAGAS_EMBED_API_KEY    openai 模式必填；兼容别名 DASHSCOPE_EMBED_API_KEY
    RAGAS_EMBED_BASE_URL   openai 模式端点（默认 dashscope.aliyuncs.com 兼容端点）
    RAGAS_EMBED_MODEL      openai 模式的模型名（默认 text-embedding-v3）
    RAGAS_EMBED_DIM        charngram 模式的向量维度（默认 1024）
"""
import hashlib
import math
import os

from langchain_core.embeddings import Embeddings
from ragas.embeddings import LangchainEmbeddingsWrapper


class CharNgramEmbeddings(Embeddings):
    """字符 n-gram 哈希向量：离线、确定性、无第三方依赖，用于近似相似度。"""

    def __init__(self, dim: int = 1024, ngram: int = 2):
        self.dim = dim
        self.ngram = ngram

    def _embed(self, text: str):
        vec = [0.0] * self.dim
        t = (text or "").lower()
        for i in range(len(t) - self.ngram + 1):
            gram = t[i : i + self.ngram]
            idx = int(hashlib.md5(gram.encode("utf-8")).hexdigest(), 16) % self.dim
            vec[idx] += 1.0
        norm = math.sqrt(sum(v * v for v in vec))
        if norm > 0:
            vec = [v / norm for v in vec]
        return vec

    def embed_documents(self, texts):
        return [self._embed(t) for t in texts]

    def embed_query(self, text):
        return self._embed(text)


def build_embeddings():
    provider = os.getenv("RAGAS_EMBED_PROVIDER", "charngram")
    if provider == "openai":
        from langchain_openai import OpenAIEmbeddings

        base = os.getenv(
            "RAGAS_EMBED_BASE_URL",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
        )
        key = os.getenv("RAGAS_EMBED_API_KEY") or os.getenv("DASHSCOPE_EMBED_API_KEY")
        if not key:
            raise RuntimeError(
                "RAGAS_EMBED_PROVIDER=openai 需要 RAGAS_EMBED_API_KEY（即后端 dashscop-embed "
                "Provider 的 API Key，可在「设置→模型服务」里查看/复制）"
            )
        model = os.getenv("RAGAS_EMBED_MODEL", "text-embedding-v3")
        return LangchainEmbeddingsWrapper(
            OpenAIEmbeddings(base_url=base, api_key=key, model=model)
        )

    dim = int(os.getenv("RAGAS_EMBED_DIM", "1024"))
    return LangchainEmbeddingsWrapper(CharNgramEmbeddings(dim=dim))
