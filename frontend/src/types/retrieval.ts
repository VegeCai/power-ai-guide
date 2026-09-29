// 检索命中来源（智能检索机制：回答下方标注引用的文档片段）
export interface RetrievalSource {
  kbId: number | null;
  documentName: string | null;
  sectionPath: string | null;
  content: string;
  score: number;
}
