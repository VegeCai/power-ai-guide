export interface ProviderItem {
  id: string;
  baseUrl: string;
  maskedApiKey: string;
  model: string;
  embeddingModel: string | null;
  embeddingDimensions: number | null;
  supportsEmbedding: boolean;
  rerankModel: string | null;
  supportsRerank: boolean;
  rerankUrl: string | null;
  rerankApiStyle: string | null;
  temperature: number | null;
  defaultChatProvider: boolean;
  defaultEmbeddingProvider: boolean;
  defaultRerankProvider: boolean;
}

export interface CreateProviderRequest {
  id: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  embeddingModel?: string;
  embeddingDimensions?: number;
  supportsEmbedding?: boolean;
  rerankModel?: string;
  supportsRerank?: boolean;
  rerankUrl?: string;
  rerankApiStyle?: string;
  temperature?: number;
}

export interface UpdateProviderRequest {
  baseUrl?: string;
  apiKey?: string;
  model?: string;
  embeddingModel?: string;
  embeddingDimensions?: number;
  supportsEmbedding?: boolean;
  rerankModel?: string;
  supportsRerank?: boolean;
  rerankUrl?: string;
  rerankApiStyle?: string;
  temperature?: number;
}

export interface ProviderTestResult {
  success: boolean;
  message: string;
  model: string;
}

export interface DefaultProvider {
  defaultProvider: string;
  defaultEmbeddingProvider: string;
  defaultRerankProvider: string;
}
