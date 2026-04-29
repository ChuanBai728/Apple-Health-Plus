'use client';

import { useState, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { createUploadWithProgress, completeUpload, loadDemo } from '@/lib/api';

function formatSize(bytes: number) {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export default function HomePage() {
  const router = useRouter();
  const fileInput = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState({ loaded: 0, total: 0, percent: 0 });
  const [phase, setPhase] = useState<'idle' | 'uploading' | 'processing'>('idle');
  const [error, setError] = useState('');

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) {
      if (!f.name.toLowerCase().endsWith('.zip')) { setError('仅支持 .zip 文件'); return; }
      setFile(f);
      setError('');
    }
  };

  const handleUpload = useCallback(async () => {
    if (!file) { setError('请选择文件'); return; }
    setUploading(true);
    setError('');
    setPhase('uploading');

    try {
      const result = await createUploadWithProgress(file, (p) => setProgress({ ...p }));
      setPhase('processing');
      await completeUpload(result.uploadId);
      router.push(`/uploads/${result.uploadId}`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '上传失败，请重试');
      setUploading(false);
      setPhase('idle');
    }
  }, [file, router]);

  const handleDemo = useCallback(async () => {
    setUploading(true);
    setError('');
    setPhase('processing');
    try {
      const result = await loadDemo();
      router.push(`/uploads/${result.uploadId}`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '加载示例数据失败');
      setUploading(false);
      setPhase('idle');
    }
  }, [router]);

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const f = e.dataTransfer.files[0];
    if (f) {
      if (!f.name.toLowerCase().endsWith('.zip')) { setError('仅支持 .zip 文件'); return; }
      setFile(f);
      setError('');
    }
  };

  return (
    <div className="max-w-6xl mx-auto px-4">
      {/* Hero */}
      <div className="text-center space-y-5 pt-6">
        <div className="inline-flex items-center gap-2 px-4 py-1.5 bg-[#007AFF]/8 text-[#007AFF] rounded-full text-sm font-medium">
          <span className="w-2 h-2 bg-blue-500 rounded-full animate-pulse" />
          基于 DeepSeek AI 深度分析
        </div>
        <h1 className="text-4xl font-extrabold tracking-tight text-[#1C1C1E] leading-tight">
          Apple Health+
          <br />
          苹果健康数据可视化与 AI 洞察
        </h1>
        <p className="text-lg text-[#8E8E93] max-w-xl mx-auto leading-relaxed">
          上传 Apple Health 导出的 导出.zip，自动生成健康指标图表、趋势分析，与 AI 深度对话你的健康数据
        </p>
      </div>

      {/* Main content */}
      <div className="space-y-8">
          {/* How to Export Guide */}
          <div className="max-w-lg mx-auto bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF] rounded-3xl p-6 border border-black/5">
            <h3 className="text-sm font-semibold text-[#007AFF] mb-3">如何获取导出数据？</h3>
            <div className="flex items-start gap-3">
              <div className="flex-shrink-0 w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center text-blue-600 text-sm font-bold">1</div>
              <p className="text-sm text-slate-700">打开 iPhone 上的 <strong>“健康”</strong> App</p>
            </div>
            <div className="flex items-start gap-3 mt-2">
              <div className="flex-shrink-0 w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center text-blue-600 text-sm font-bold">2</div>
              <p className="text-sm text-slate-700">点击右上角 <strong>头像</strong>（或个人资料图标）</p>
            </div>
            <div className="flex items-start gap-3 mt-2">
              <div className="flex-shrink-0 w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center text-blue-600 text-sm font-bold">3</div>
              <p className="text-sm text-slate-700">下滑到底部，点击 <strong>“导出所有健康数据”</strong></p>
            </div>
            <div className="flex items-start gap-3 mt-2">
              <div className="flex-shrink-0 w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center text-blue-600 text-sm font-bold">4</div>
              <p className="text-sm text-slate-700">保存生成的 <code className="bg-blue-100 px-1.5 py-0.5 rounded text-blue-700 text-xs font-mono">导出.zip</code> 文件</p>
            </div>
            <div className="flex items-start gap-3 mt-2">
              <div className="flex-shrink-0 w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center text-blue-600 text-sm font-bold">5</div>
              <p className="text-sm text-slate-700">在此页面拖拽或选择该文件上传分析</p>
            </div>
          </div>

          {/* Upload Zone */}
          <div
            onDrop={handleDrop}
            onDragOver={(e) => e.preventDefault()}
            className={`max-w-lg mx-auto rounded-3xl p-8 text-center transition-all duration-300 ${
              uploading
                ? 'bg-gradient-to-br from-[#F8F8FC] to-[#EEF0FF] border-2 border-[#007AFF]/20 shadow-lg'
                : 'bg-white border-2 border-dashed border-black/10 hover:border-black/20 hover:shadow-md'
            }`}
          >
        {uploading ? (
          <div className="space-y-5">
            <div className="w-16 h-16 mx-auto bg-blue-100 rounded-3xl flex items-center justify-center text-3xl animate-pulse">
              {phase === 'uploading' ? '📤' : '⏳'}
            </div>
            <p className="text-gray-800 font-semibold">
              {phase === 'uploading'
                ? `上传中 ${progress.percent}%`
                : '正在提交任务...'}
            </p>
            {phase === 'uploading' && (
              <>
                <p className="text-sm text-[#8E8E93]">
                  {formatSize(progress.loaded)} / {formatSize(progress.total)}
                </p>
                <div className="w-full bg-black/5 rounded-full h-2.5 overflow-hidden">
                  <div
                    className="h-full rounded-full bg-gradient-to-r bg-[#007AFF] transition-all duration-300"
                    style={{ width: `${progress.percent}%` }}
                  />
                </div>
              </>
            )}
            {phase === 'processing' && (
              <div className="w-full bg-black/5 rounded-full h-2.5 overflow-hidden">
                <div className="h-full w-full rounded-full bg-[#007AFF] animate-shimmer"
                  style={{ backgroundSize: '200% 100%' }} />
              </div>
            )}
          </div>
        ) : !file ? (
          <div className="space-y-4">
            <div className="w-14 h-14 mx-auto bg-blue-50 rounded-3xl flex items-center justify-center text-2xl">
              📁
            </div>
            <div>
              <p className="text-slate-700 font-medium">拖拽 导出.zip 到此处</p>
              <p className="text-xs text-[#8E8E93]/70 mt-1">或点击下方按钮选择文件</p>
            </div>
            <input ref={fileInput} type="file" accept=".zip" onChange={handleFileChange} className="hidden" />
            <button
              onClick={() => fileInput.current?.click()}
              className="px-8 py-3 bg-[#007AFF] text-white rounded-full hover:bg-[#0077EE] transition-all font-semibold shadow-lg shadow-[#007AFF]/20"
            >
              选择文件
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="w-14 h-14 mx-auto bg-[#34C759]/5 rounded-3xl flex items-center justify-center text-2xl">
              ✅
            </div>
            <p className="text-gray-800 font-semibold truncate">{file.name}</p>
            <p className="text-sm text-[#8E8E93]">{formatSize(file.size)}</p>
            <button
              onClick={handleUpload}
              className="w-full py-3 bg-[#007AFF] text-white rounded-full hover:bg-[#0077EE] transition-all text-lg font-bold shadow-xl shadow-[#007AFF]/20"
            >
              开始分析
            </button>
            <button onClick={() => { setFile(null); setError(''); }} className="text-sm text-[#8E8E93]/70 hover:text-gray-600">
              重新选择
            </button>
          </div>
        )}
        {error && (
          <p className="mt-4 text-sm text-[#FF3B30] bg-[#FF3B30]/5 rounded-3xl px-4 py-3 border border-[#FF3B30]/10">{error}</p>
        )}
      </div>

          {/* Demo Preview */}
          <div className="max-w-lg mx-auto text-center">
            <div className="text-xs text-[#8E8E93]/70 mb-2">不想上传真实数据？试试示例</div>
            <button
              onClick={handleDemo}
              disabled={uploading}
              className="px-6 py-2.5 bg-white border-2 border-[#5856D6]/20 text-[#5856D6] rounded-3xl hover:bg-[#5856D6]/5 hover:border-[#5856D6]/30 disabled:opacity-40 transition-all text-sm font-semibold shadow-[0_8px_30px_rgb(0,0,0,0.04)]"
            >
              🚀 快速预览示例数据
            </button>
          </div>

          {/* Feature Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-lg mx-auto">
            {[
              { icon: '📊', title: '自动解析', desc: '流式解析超大 XML，处理百万条健康记录', color: 'bg-[#007AFF]/5 border-[#007AFF]/10' },
              { icon: '📈', title: '趋势图表', desc: '心率、睡眠、步数等核心指标日/周/月趋势', color: 'bg-[#34C759]/5 border-[#34C759]/10' },
              { icon: '🤖', title: 'AI 深度对话', desc: '基于真实数据，DeepSeek 多轮分析健康状态', color: 'bg-[#5856D6]/5 border-[#5856D6]/10' },
            ].map((f) => (
              <div key={f.title} className={`bg-white ${f.color} border rounded-3xl p-5 text-center`}>
                <div className="text-2xl mb-2">{f.icon}</div>
                <h3 className="font-bold text-[#1C1C1E] text-sm mb-1">{f.title}</h3>
                <p className="text-xs text-gray-600 leading-relaxed">{f.desc}</p>
              </div>
            ))}
          </div>
      </div>

      <p className="text-center text-xs text-[#8E8E93]/70 max-w-md mx-auto pb-4 pt-8">
        你的健康数据仅用于本次分析，不会与第三方共享。本产品不提供医疗诊断，AI 分析仅供参考。
      </p>
    </div>
  );
}
