"""跑 ragas 评测：读 testset.json → 逐条调 POST /api/rag-eval → 组装 EvaluationDataset → 跑指标。

前置条件：
1. 后端已启动（默认 http://localhost:8080），且知识库已向量化。
2. 已用 generate_testset.py 生成 testset.json。
3. 项目根目录 .env 中有 AI_BAILIAN_API_KEY，依赖已装（requirements.txt）。

用法：
    python run_evaluation.py
可用环境变量：
    POWER_AI_BASE_URL  后端地址，默认 http://localhost:8080
    RAGAS_TEST_FILE    输入测试集，默认 testset.json
    RAGAS_KB_IDS       指定检索的知识库 ID（逗号分隔），默认全部已完成向量化的库
    RAGAS_EMBED_PROVIDER / RAGAS_EMBED_API_KEY / RAGAS_EMBED_MODEL  见 embedding.py
        默认 charngram 离线兜底；要跑可信指标用 RAGAS_EMBED_PROVIDER=openai 切到真实 embedding
"""
import math
import os
from pathlib import Path

import pandas as pd
import requests
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from ragas import EvaluationDataset, evaluate
from ragas.dataset_schema import SingleTurnSample
from ragas.llms import LangchainLLMWrapper

from embedding import build_embeddings
from ragas.metrics import (
    AnswerRelevancy,
    ContextPrecision,
    ContextRecall,
    Faithfulness,
)

# 复用项目根目录 .env 里的 AI_BAILIAN_API_KEY（脚本位于 scripts/ragas/）
load_dotenv(Path(__file__).resolve().parents[2] / ".env")

BASE = os.getenv("POWER_AI_BASE_URL", "http://localhost:8080")
DASHSCOPE_BASE = os.getenv(
    "DASHSCOPE_BASE_URL",
    "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
)
API_KEY = os.environ["AI_BAILIAN_API_KEY"]
CHAT_MODEL = os.getenv("AI_MODEL", "qwen3.8-flash")  # 与项目 .env 的 AI_MODEL 保持一致
TEST_FILE = os.getenv("RAGAS_TEST_FILE", "testset.json")


def resolve_kb_ids() -> list[int]:
    """确定每次提问的检索范围：默认全部已完成向量化的知识库，可用 RAGAS_KB_IDS 覆盖。"""
    explicit = os.getenv("RAGAS_KB_IDS", "")
    if explicit.strip():
        return [int(x) for x in explicit.split(",") if x.strip()]

    resp = requests.get(f"{BASE}/api/knowledgebase/list")
    resp.raise_for_status()
    ids = [
        int(kb["id"])
        for kb in resp.json()["data"]
        if kb.get("vectorStatus") == "COMPLETED"
    ]
    if not ids:
        raise SystemExit("没有已完成向量化的知识库，无法评测")
    return ids


# 1) 读测试集
testset = pd.read_json(TEST_FILE)
print(f"已读入 {len(testset)} 条测试用例: {TEST_FILE}")

kb_ids = resolve_kb_ids()
print(f"检索范围（知识库 ID）: {kb_ids}")

# 2) 逐条调用后端 RAG 接口，取 answer + contexts，组装样本
samples = []
skipped = 0
for i, row in testset.iterrows():
    question = row["user_input"]
    try:
        resp = requests.post(
            f"{BASE}/api/rag-eval",
            json={"knowledgeBaseIds": kb_ids, "question": question},
            timeout=180,
        )
        resp.raise_for_status()
        data = resp.json()["data"]
    except Exception as exc:  # 单条失败不中断整体
        print(f"[跳过] 第 {i} 条调用失败: {exc}")
        skipped += 1
        continue

    contexts = data.get("contexts") or []
    if not contexts:
        skipped += 1
        continue

    reference = row.get("reference")
    if reference is not None and isinstance(reference, float) and math.isnan(reference):
        reference = None

    samples.append(
        SingleTurnSample(
            user_input=question,
            response=data["answer"],
            retrieved_contexts=contexts,
            reference=reference,
        )
    )

if skipped:
    print(f"跳过 {skipped} 条（调用失败或检索结果为空）")
if not samples:
    raise SystemExit("没有可用样本，无法评测")

dataset = EvaluationDataset(samples=samples)

# 3) 初始化评测器（LLM 复用百炼 DashScope；Embedding 用 embedding.py 的兜底）
llm = LangchainLLMWrapper(
    ChatOpenAI(base_url=DASHSCOPE_BASE, api_key=API_KEY, model=CHAT_MODEL)
)
emb = build_embeddings()

# 4) 跑指标（可按需追加 AnswerCorrectness / AnswerSimilarity）
metrics = [Faithfulness(), AnswerRelevancy(), ContextPrecision(), ContextRecall()]
result = evaluate(dataset=dataset, metrics=metrics, llm=llm, embeddings=emb)

# 5) 输出报告
report = result.to_pandas()
report.to_csv("evaluation_report.csv", index=False, encoding="utf-8-sig")

print("\n===== 评测结果（汇总）=====")
print(result)
print("\n各指标平均分：")
print(report.select_dtypes(include="number").mean())
print(f"\n逐样本结果已写出到 evaluation_report.csv")
