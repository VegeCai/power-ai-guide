import { request } from './request';
import type {
  DocumentType,
  CreateDocumentTypeRequest,
  UpdateDocumentTypeRequest,
} from '../types/documentType';

export const documentTypeApi = {
  /** 获取所有文档类型 */
  list: () => request.get<DocumentType[]>('/api/document-types'),

  /** 创建文档类型 */
  create: (data: CreateDocumentTypeRequest) =>
    request.post<DocumentType>('/api/document-types', data),

  /** 更新文档类型 */
  update: (id: number, data: UpdateDocumentTypeRequest) =>
    request.put<DocumentType>(`/api/document-types/${id}`, data),

  /** 删除文档类型 */
  delete: (id: number) => request.delete<void>(`/api/document-types/${id}`),
};
