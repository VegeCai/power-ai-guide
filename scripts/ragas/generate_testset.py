"""从当前项目的知识库分块自动合成 ragas 评测测试集。

前置条件：
1. 后端已启动（默认 http://localhost:8080），且目标知识库已完成向量化。
2. 项目根目录 .env 中有 AI_BAILIAN_API_KEY（阿里云百炼）。
3. 已按 requirements.txt 安装依赖。

用法：
    python generate_testset.py
可用环境变量覆盖默认值：
    POWER_AI_BASE_URL  后端地址，默认 http://localhost:8080
    RAGAS_TESTSET_SIZE 目标测试用例数，默认 50
    AI_MODEL           生成/抽取用的聊天模型，默认 qwen3.8-flash
    RAGAS_EMBED_PROVIDER / RAGAS_EMBED_API_KEY / RAGAS_EMBED_MODEL  见 embedding.py

说明：项目把「聊天」和「向量」拆成两个 Provider，真实 embedding 走阿里云百炼
dashscope.aliyuncs.com（Provider dashscop-embed，模型 text-embedding-v3）。脚本默认用
embedding.py 的「字符 n-gram 哈希」离线兜底（无需额外 key）；要得到可信的评测结果，
设 RAGAS_EMBED_PROVIDER=openai + RAGAS_EMBED_API_KEY=<dashscop-embed 的 key> 切到真实向量。
"""
import os
from pathlib import Path

import requests
from dotenv import load_dotenv
from langchain_core.documents import Document
from langchain_openai import ChatOpenAI
from ragas.llms import LangchainLLMWrapper
from ragas.testset import TestsetGenerator

from embedding import build_embeddings

# 复用项目根目录 .env 里的 AI_BAILIAN_API_KEY（脚本位于 scripts/ragas/）
load_dotenv(Path(__file__).resolve().parents[2] / ".env")

BASE = os.getenv("POWER_AI_BASE_URL", "http://localhost:8080")
DASHSCOPE_BASE = os.getenv(
    "DASHSCOPE_BASE_URL",
    "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
)
API_KEY = os.environ["AI_BAILIAN_API_KEY"]
CHAT_MODEL = os.getenv("AI_MODEL", "qwen3.8-flash")  # 与项目 .env 的 AI_MODEL 保持一致
SIZE = int(os.getenv("RAGAS_TESTSET_SIZE", "50"))

# 1) 拉取全部已完成向量化知识库的分块（也可加查询参数 ?kbIds=1,2,3 指定范围）
resp = requests.get(f"{BASE}/api/rag-eval/documents")
resp.raise_for_status()
rows = resp.json()["data"]
if not rows:
    raise SystemExit("未获取到任何分块，请确认知识库已向量化（vectorStatus=COMPLETED）")

docs = [
    Document(
        page_content=d["content"],
        metadata={
            "kb_id": d["kbId"],
            "kb_name": d["kbName"],
            "section_path": d["sectionPath"],
            "heading": d["heading"],
        },
    )
    for d in rows
]
print(f"已加载 {len(docs)} 个分块，目标生成 {SIZE} 条测试用例")

# 2) 生成器：LLM 复用百炼 DashScope（OpenAI 兼容模式）；Embedding 用 embedding.py 的兜底
llm = LangchainLLMWrapper(
    ChatOpenAI(base_url=DASHSCOPE_BASE, api_key=API_KEY, model=CHAT_MODEL)
)
emb = build_embeddings()
generator = TestsetGenerator(llm=llm, embedding_model=emb)

# 3) 生成并导出
testset = generator.generate_with_langchain_docs(docs, testset_size=SIZE)
df = testset.to_pandas()
df.to_csv("testset.csv", index=False, encoding="utf-8-sig")
df.to_json("testset.json", orient="records", force_ascii=False, indent=2)
print(f"已生成 {len(df)} 条测试用例，写出到 testset.csv / testset.json")
