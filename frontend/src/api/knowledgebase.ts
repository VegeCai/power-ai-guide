import { request } from './request';
import { streamSse } from './stream';
import type { RetrievalSource } from '../types/retrieval';

// 向量化状态
export type VectorStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface KnowledgeBaseItem {
  id: number;
  name: string;
  category: string | null;
  documentTypeId: number | null;
  originalFilename: string;
  fileSize: number;
  contentType: string;
  uploadedAt: string;
  lastAccessedAt: string;
  accessCount: number;
  questionCount: number;
  vectorStatus: VectorStatus;
  vectorError: string | null;
  chunkCount: number;
}

// 统计信息
export interface KnowledgeBaseStats {
  totalCount: number;
  totalQuestionCount: number;
  totalAccessCount: number;
  completedCount: number;
  processingCount: number;
}

export type SortOption = 'time' | 'size' | 'access' | 'question';

export interface UploadKnowledgeBaseResponse {
  knowledgeBase: {
    id: number;
    name: string;
    category: string;
    fileSize: number;
    contentLength: number;
  };
  storage: {
    fileKey: string;
    fileUrl: string;
  };
  duplicate: boolean;
}

// 批量上传：单个文件结果
export interface UploadKnowledgeBaseBatchItem {
  fileName: string;
  success: boolean;
  duplicate: boolean;
  knowledgeBaseId: number | null;
  name: string | null;
  category: string | null;
  fileSize: number | null;
  contentLength: number | null;
  fileKey: string | null;
  fileUrl: string | null;
  errorMessage: string | null;
}

// 批量上传响应
export interface UploadKnowledgeBaseBatchResponse {
  total: number;
  successCount: number;
  failedCount: number;
  results: UploadKnowledgeBaseBatchItem[];
}

export interface QueryRequest {
  knowledgeBaseIds?: number[];  // 支持多个知识库（可与文档类型取并集）
  documentTypeIds?: number[];   // 文档类型ID列表（可选）
  question: string;
}

export interface QueryResponse {
  answer: string;
  knowledgeBaseId: number;
  knowledgeBaseName: string;
  sources: RetrievalSource[];
}

export const knowledgeBaseApi = {
  /**
   * 上传知识库文件
   */
  async uploadKnowledgeBase(file: File, name?: string, category?: string, documentTypeId?: number): Promise<UploadKnowledgeBaseResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (name) {
      formData.append('name', name);
    }
    if (category) {
      formData.append('category', category);
    }
    if (documentTypeId) {
      formData.append('documentTypeId', String(documentTypeId));
    }
    return request.upload<UploadKnowledgeBaseResponse>('/api/knowledgebase/upload', formData);
  },

  /**
   * 批量上传知识库文件（分类、文档类型整批共享，名称默认取文件名）
   */
  async uploadKnowledgeBaseBatch(files: File[], category?: string, documentTypeId?: number): Promise<UploadKnowledgeBaseBatchResponse> {
    const formData = new FormData();
    files.forEach((f) => formData.append('files', f));
    if (category) {
      formData.append('category', category);
    }
    if (documentTypeId) {
      formData.append('documentTypeId', String(documentTypeId));
    }
    return request.upload<UploadKnowledgeBaseBatchResponse>('/api/knowledgebase/upload/batch', formData);
  },

  /**
   * 下载知识库文件
   */
  async downloadKnowledgeBase(id: number): Promise<Blob> {
    return request.download(`/api/knowledgebase/${id}/download`);
  },

  /**
   * 获取所有知识库列表
   */
  async getAllKnowledgeBases(sortBy?: SortOption, vectorStatus?: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'): Promise<KnowledgeBaseItem[]> {
    const params = new URLSearchParams();
    if (sortBy) {
      params.append('sortBy', sortBy);
    }
    if (vectorStatus) {
      params.append('vectorStatus', vectorStatus);
    }
    const queryString = params.toString();
    return request.get<KnowledgeBaseItem[]>(`/api/knowledgebase/list${queryString ? `?${queryString}` : ''}`);
  },

  /**
   * 获取知识库详情
   */
  async getKnowledgeBase(id: number): Promise<KnowledgeBaseItem> {
    return request.get<KnowledgeBaseItem>(`/api/knowledgebase/${id}`);
  },

  /**
   * 删除知识库
   */
  async deleteKnowledgeBase(id: number): Promise<void> {
    return request.delete(`/api/knowledgebase/${id}`);
  },

  // ========== 分类管理 ==========

  /**
   * 获取所有分类
   */
  async getAllCategories(): Promise<string[]> {
    return request.get<string[]>('/api/knowledgebase/categories');
  },

  /**
   * 根据分类获取知识库
   */
  async getByCategory(category: string): Promise<KnowledgeBaseItem[]> {
    return request.get<KnowledgeBaseItem[]>(`/api/knowledgebase/category/${encodeURIComponent(category)}`);
  },

  /**
   * 获取未分类的知识库
   */
  async getUncategorized(): Promise<KnowledgeBaseItem[]> {
    return request.get<KnowledgeBaseItem[]>('/api/knowledgebase/uncategorized');
  },

  /**
   * 更新知识库分类
   */
  async updateCategory(id: number, category: string | null): Promise<void> {
    return request.put(`/api/knowledgebase/${id}/category`, { category });
  },

  /**
   * 更新知识库文档类型
   */
  async updateDocumentType(id: number, documentTypeId: number | null): Promise<void> {
    return request.put(`/api/knowledgebase/${id}/document-type`, { documentTypeId });
  },

  // ========== 搜索 ==========

  /**
   * 搜索知识库
   */
  async search(keyword: string): Promise<KnowledgeBaseItem[]> {
    return request.get<KnowledgeBaseItem[]>(`/api/knowledgebase/search?keyword=${encodeURIComponent(keyword)}`);
  },

  // ========== 统计 ==========

  /**
   * 获取知识库统计信息
   */
  async getStatistics(): Promise<KnowledgeBaseStats> {
    return request.get<KnowledgeBaseStats>('/api/knowledgebase/stats');
  },

  // ========== 向量化管理 ==========

  /**
   * 重新向量化知识库（手动重试）
   */
  async revectorize(id: number): Promise<void> {
    return request.post(`/api/knowledgebase/${id}/revectorize`);
  },

  /**
   * 基于知识库回答问题
   */
  async queryKnowledgeBase(req: QueryRequest): Promise<QueryResponse> {
    return request.post<QueryResponse>('/api/knowledgebase/query', req, {
      timeout: 180000, // 3分钟超时
    });
  },

  /**
   * 基于知识库回答问题（流式SSE）
   * 注意：SSE 使用 fetch API，不走统一的 axios 封装
   */
  async queryKnowledgeBaseStream(
    req: QueryRequest,
    onMessage: (chunk: string) => void,
    onComplete: () => void,
    onError: (error: Error) => void
  ): Promise<void> {
    return streamSse({
      url: '/api/knowledgebase/query/stream',
      init: {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(req),
      },
      onMessage,
      onComplete,
      onError,
      parseMode: 'line',
      trimDataPrefixSpace: true,
    });
  },
};
