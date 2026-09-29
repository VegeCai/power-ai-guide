import {useCallback, useEffect, useState} from 'react';
import {AnimatePresence, motion} from 'framer-motion';
import {ArrowLeft, FileText, Loader2, Pencil, Plus, Tag, Trash2} from 'lucide-react';
import {documentTypeApi} from '../api/documentType';
import type {DocumentType} from '../types/documentType';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';

interface DocumentTypeManagePageProps {
  onBack: () => void;
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

export default function DocumentTypeManagePage({ onBack }: DocumentTypeManagePageProps) {
  const [types, setTypes] = useState<DocumentType[]>([]);
  const [loading, setLoading] = useState(true);

  // 新建/编辑弹窗
  const [showModal, setShowModal] = useState(false);
  const [editingType, setEditingType] = useState<DocumentType | null>(null);
  const [formName, setFormName] = useState('');
  const [formDescription, setFormDescription] = useState('');
  const [saving, setSaving] = useState(false);

  // 删除确认
  const [deleteType, setDeleteType] = useState<DocumentType | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Toast
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const loadData = useCallback(async () => {
    try {
      const list = await documentTypeApi.list();
      setTypes(list);
    } catch (err) {
      console.error('加载文档类型失败', err);
      showToast('error', '加载文档类型失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const showToast = (type: 'success' | 'error', message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 3000);
  };

  const openCreateModal = () => {
    setEditingType(null);
    setFormName('');
    setFormDescription('');
    setShowModal(true);
  };

  const openEditModal = (type: DocumentType) => {
    setEditingType(type);
    setFormName(type.name);
    setFormDescription(type.description ?? '');
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingType(null);
    setFormName('');
    setFormDescription('');
  };

  const handleSave = async () => {
    const name = formName.trim();
    if (!name) {
      showToast('error', '类型名称不能为空');
      return;
    }
    try {
      setSaving(true);
      if (editingType) {
        await documentTypeApi.update(editingType.id, { name, description: formDescription.trim() || undefined });
        showToast('success', '文档类型已更新');
      } else {
        await documentTypeApi.create({ name, description: formDescription.trim() || undefined });
        showToast('success', '文档类型已创建');
      }
      closeModal();
      await loadData();
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteType) return;
    try {
      setDeleting(true);
      await documentTypeApi.delete(deleteType.id);
      showToast('success', '文档类型已删除');
      setDeleteType(null);
      await loadData();
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : '删除失败');
      setDeleteType(null);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto">
      {/* 头部 */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
            title="返回"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white flex items-center gap-2">
              <Tag className="w-6 h-6 text-primary-500" />
              文档类型管理
            </h1>
            <p className="text-slate-500 dark:text-slate-400 text-sm mt-0.5">管理上传文档时可选用的类型</p>
          </div>
        </div>
        <button
          onClick={openCreateModal}
          className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
        >
          <Plus className="w-4 h-4" />
          新建类型
        </button>
      </div>

      {/* 列表 */}
      <div className="bg-white dark:bg-slate-800 rounded-xl shadow-sm border border-slate-100 dark:border-slate-700 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
          </div>
        ) : types.length === 0 ? (
          <div className="text-center py-20">
            <FileText className="w-16 h-16 text-slate-300 mx-auto mb-4" />
            <p className="text-slate-500 dark:text-slate-400">暂无文档类型</p>
            <button onClick={openCreateModal} className="mt-4 text-primary-500 hover:text-primary-600">
              创建第一个类型
            </button>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-slate-50 dark:bg-slate-700 border-b border-slate-100 dark:border-slate-600">
              <tr>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">名称</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">描述</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">文档数</th>
                <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">创建时间</th>
                <th className="text-right px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">操作</th>
              </tr>
            </thead>
            <tbody>
              {types.map((type) => (
                <tr
                  key={type.id}
                  className="border-b border-slate-50 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors"
                >
                  <td className="px-6 py-4">
                    <span className="px-2 py-1 bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 rounded text-sm font-medium">
                      {type.name}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm text-slate-600 dark:text-slate-300">
                    {type.description || <span className="text-slate-400 dark:text-slate-500">—</span>}
                  </td>
                  <td className="px-6 py-4 text-sm text-slate-600 dark:text-slate-300">{type.documentCount}</td>
                  <td className="px-6 py-4 text-sm text-slate-500 dark:text-slate-400">{formatDate(type.createdAt)}</td>
                  <td className="px-6 py-4 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        onClick={() => openEditModal(type)}
                        className="p-2 text-slate-400 hover:text-primary-500 hover:bg-primary-50 dark:hover:bg-primary-900/30 rounded-lg transition-colors"
                        title="编辑"
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => setDeleteType(type)}
                        className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-lg transition-colors"
                        title="删除"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 新建/编辑弹窗 */}
      <AnimatePresence>
        {showModal && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={closeModal}
              className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50"
            />
            <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
              <motion.div
                initial={{ opacity: 0, scale: 0.95, y: 20 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95, y: 20 }}
                onClick={(e) => e.stopPropagation()}
                className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-md w-full p-6 border border-slate-100 dark:border-slate-700"
              >
                <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-4">
                  {editingType ? '编辑类型' : '新建类型'}
                </h3>
                <label className="block text-sm font-medium text-slate-600 dark:text-slate-300 mb-1">名称</label>
                <input
                  type="text"
                  value={formName}
                  onChange={(e) => setFormName(e.target.value)}
                  onKeyPress={(e) => e.key === 'Enter' && handleSave()}
                  placeholder="请输入类型名称"
                  className="w-full px-4 py-2.5 text-sm border border-slate-200 dark:border-slate-600 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 mb-4 bg-white dark:bg-slate-700 text-slate-900 dark:text-white placeholder-slate-400"
                  autoFocus
                />
                <label className="block text-sm font-medium text-slate-600 dark:text-slate-300 mb-1">描述（可选）</label>
                <input
                  type="text"
                  value={formDescription}
                  onChange={(e) => setFormDescription(e.target.value)}
                  onKeyPress={(e) => e.key === 'Enter' && handleSave()}
                  placeholder="请输入类型描述"
                  className="w-full px-4 py-2.5 text-sm border border-slate-200 dark:border-slate-600 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 mb-6 bg-white dark:bg-slate-700 text-slate-900 dark:text-white placeholder-slate-400"
                />
                <div className="flex justify-end gap-3">
                  <button
                    onClick={closeModal}
                    disabled={saving}
                    className="px-4 py-2 text-sm text-slate-600 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white disabled:opacity-50"
                  >
                    取消
                  </button>
                  <button
                    onClick={handleSave}
                    disabled={saving || !formName.trim()}
                    className="px-4 py-2 text-sm bg-primary-500 text-white rounded-lg hover:bg-primary-600 disabled:opacity-50 flex items-center gap-2"
                  >
                    {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                    保存
                  </button>
                </div>
              </motion.div>
            </div>
          </>
        )}
      </AnimatePresence>

      {/* 删除确认 */}
      <DeleteConfirmDialog
        open={deleteType !== null}
        item={deleteType ? { id: deleteType.id, name: deleteType.name } : null}
        itemType="文档类型"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteType(null)}
      />

      {/* Toast */}
      <AnimatePresence>
        {toast && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className={`fixed top-6 right-6 z-[60] px-5 py-3 rounded-xl shadow-lg text-white text-sm font-medium ${
              toast.type === 'success' ? 'bg-emerald-500' : 'bg-red-500'
            }`}
          >
            {toast.message}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
