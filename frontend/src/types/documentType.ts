export interface DocumentType {
  id: number;
  name: string;
  description: string | null;
  documentCount: number;
  createdAt: string;
}

export interface CreateDocumentTypeRequest {
  name: string;
  description?: string;
}

export interface UpdateDocumentTypeRequest {
  name: string;
  description?: string;
}
