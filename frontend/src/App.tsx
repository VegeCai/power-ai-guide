import { BrowserRouter, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import Layout from './components/Layout';
import { Suspense, lazy } from 'react';
import { ROUTES } from './constants/routes';

// Lazy load components
const KnowledgeBaseQueryPage = lazy(() => import('./pages/KnowledgeBaseQueryPage'));
const KnowledgeBaseUploadPage = lazy(() => import('./pages/KnowledgeBaseUploadPage'));
const KnowledgeBaseManagePage = lazy(() => import('./pages/KnowledgeBaseManagePage'));
const DocumentTypeManagePage = lazy(() => import('./pages/DocumentTypeManagePage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));

// Loading component
const Loading = () => (
  <div className="flex items-center justify-center min-h-[50vh]">
    <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
  </div>
);

function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<Loading />}>
        <Routes>
          <Route path="/" element={<Layout />}>
            {/* 默认重定向到知识库管理页面 */}
            <Route index element={<Navigate to="/knowledgebase" replace />} />

            {/* 知识库管理 */}
            <Route path="knowledgebase" element={<KnowledgeBaseManagePageWrapper />} />

            {/* 知识库上传 */}
            <Route path="knowledgebase/upload" element={<KnowledgeBaseUploadPageWrapper />} />

            {/* 文档类型管理 */}
            <Route path="knowledgebase/document-types" element={<DocumentTypeManagePageWrapper />} />

            {/* 问答助手（知识库聊天） */}
            <Route path="knowledgebase/chat" element={<KnowledgeBaseQueryPageWrapper />} />

            {/* 设置 */}
            <Route path="settings" element={<SettingsPage />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

function KnowledgeBaseManagePageWrapper() {
  const navigate = useNavigate();

  const handleUpload = () => {
    navigate(ROUTES.knowledgebaseUpload);
  };

  const handleChat = () => {
    navigate('/knowledgebase/chat');
  };

  return <KnowledgeBaseManagePage onUpload={handleUpload} onChat={handleChat} />;
}

// 知识库问答页面包装器
function KnowledgeBaseQueryPageWrapper() {
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/knowledgebase');
  };

  const handleUpload = () => {
    navigate(ROUTES.knowledgebaseUpload);
  };

  return <KnowledgeBaseQueryPage onBack={handleBack} onUpload={handleUpload} />;
}

// 知识库上传页面包装器
function KnowledgeBaseUploadPageWrapper() {
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/knowledgebase');
  };

  const handleManageTypes = () => {
    navigate(ROUTES.documentTypes);
  };

  return <KnowledgeBaseUploadPage onBack={handleBack} onManageTypes={handleManageTypes} />;
}

// 文档类型管理页面包装器
function DocumentTypeManagePageWrapper() {
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/knowledgebase');
  };

  return <DocumentTypeManagePage onBack={handleBack} />;
}

export default App;
