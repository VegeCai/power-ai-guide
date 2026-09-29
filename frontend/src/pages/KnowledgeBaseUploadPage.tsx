import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, ChevronDown, RotateCcw, Tag, XCircle } from 'lucide-react';
import { knowledgeBaseApi } from '../api/knowledgebase';
import type { UploadKnowledgeBaseBatchResponse } from '../api/knowledgebase';
import { documentTypeApi } from '../api/documentType';
import type { DocumentType } from '../types/documentType';
import FileUploadCard from '../components/FileUploadCard';

interface KnowledgeBaseUploadPageProps {
  onBack: () => void;
  onManageTypes: () => void;
}

export default function KnowledgeBaseUploadPage({ onBack, onManageTypes }: KnowledgeBaseUploadPageProps) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [results, setResults] = useState<UploadKnowledgeBaseBatchResponse | null>(null);

  // 文档类型
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [selectedTypeId, setSelectedTypeId] = useState<number | null>(null);

  useEffect(() => {
    documentTypeApi.list()
      .then(setDocumentTypes)
      .catch(() => setDocumentTypes([]));
  }, []);

  const handleUploadFiles = async (files: File[]) => {
    if (selectedTypeId == null) {
      setError('请先选择文档类型');
      return;
    }
    setUploading(true);
    setError('');
    setResults(null);
    try {
      const data = await knowledgeBaseApi.uploadKnowledgeBaseBatch(files, undefined, selectedTypeId);
      setResults(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '上传失败，请重试');
    } finally {
      setUploading(false);
    }
  };

  if (results) {
    return (
      <motion.div
        className="max-w-3xl mx-auto pt-16"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <div className="text-center mb-8">
          <h1 className="text-4xl md:text-5xl font-bold text-slate-900 dark:text-white mb-4 tracking-tight">
            上传结果
          </h1>
          <p className="text-lg text-slate-500 dark:text-slate-400">
            成功 {results.successCount} 个，失败 {results.failedCount} 个，共 {results.total} 个
          </p>
        </div>

        <div className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-lg dark:shadow-slate-900/50 space-y-3">
          {results.results.map((item, i) => (
            <div
              key={`${item.fileName}-${i}`}
              className="flex items-center gap-4 px-4 py-3 rounded-xl bg-slate-50 dark:bg-slate-700/50"
            >
              {item.success ? (
                <CheckCircle2 className="w-5 h-5 shrink-0 text-emerald-500" />
              ) : (
                <XCircle className="w-5 h-5 shrink-0 text-red-500" />
              )}
              <div className="text-left flex-1 min-w-0">
                <p className="font-medium text-slate-900 dark:text-white truncate">
                  {item.fileName}
                  {item.duplicate && <span className="ml-2 text-xs text-amber-500">重复</span>}
                </p>
                {!item.success && item.errorMessage && (
                  <p className="text-sm text-red-500 truncate">{item.errorMessage}</p>
                )}
              </div>
            </div>
          ))}
        </div>

        <div className="mt-8 flex gap-4 justify-center">
          <button
            onClick={() => setResults(null)}
            className="px-6 py-3 border border-slate-200 dark:border-slate-600 rounded-xl text-slate-600 dark:text-slate-300 font-medium hover:bg-slate-50 dark:hover:bg-slate-700 transition-all flex items-center gap-2"
          >
            <RotateCcw className="w-4 h-4" />
            继续上传
          </button>
          <button
            onClick={onBack}
            className="px-8 py-3 bg-gradient-to-r from-emerald-500 to-emerald-600 text-white rounded-xl font-semibold shadow-lg shadow-emerald-500/30 hover:shadow-xl transition-all"
          >
            完成
          </button>
        </div>
      </motion.div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto pt-8">
      {/* 文档类型选择 */}
      <div className="mb-6 bg-white dark:bg-slate-800 rounded-2xl p-5 shadow-sm border border-slate-100 dark:border-slate-700">
        <div className="flex items-center justify-between mb-3">
          <label className="text-sm font-semibold text-slate-700 dark:text-slate-300 flex items-center gap-1.5">
            <Tag className="w-4 h-4 text-primary-500" />
            文档类型
            <span className="text-red-500">*</span>
          </label>
          <button
            onClick={onManageTypes}
            className="text-sm text-primary-500 hover:text-primary-600 font-medium"
          >
            管理类型
          </button>
        </div>
        <div className="relative">
          <select
            value={selectedTypeId ?? ''}
            onChange={(e) => {
              setSelectedTypeId(e.target.value ? Number(e.target.value) : null);
              setError('');
            }}
            className="appearance-none w-full pl-4 pr-10 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white dark:bg-slate-700 text-slate-900 dark:text-white cursor-pointer"
          >
            <option value="" disabled>请选择文档类型</option>
            {documentTypes.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
        </div>
        {documentTypes.length === 0 && (
          <p className="text-xs text-slate-400 dark:text-slate-500 mt-2">
            暂无文档类型，请先
            <button onClick={onManageTypes} className="text-primary-500 hover:text-primary-600 font-medium">
              创建类型
            </button>
          </p>
        )}
      </div>

      <FileUploadCard
        title="上传知识库"
        subtitle="上传文档，AI 将基于知识库内容回答您的问题（支持多选）"
        accept=".pdf,.doc,.docx,.txt,.md"
        formatHint="支持 PDF、DOCX、DOC、TXT、MD"
        maxSizeHint="单文件最大 50MB"
        uploading={uploading}
        uploadButtonText="开始上传"
        selectButtonText="选择文件"
        multiple
        error={error}
        onUploadFiles={handleUploadFiles}
        onBack={onBack}
      />
    </div>
  );
}
