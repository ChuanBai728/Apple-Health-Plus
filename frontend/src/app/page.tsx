'use client';

import { useState, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { createUploadWithProgress, completeUpload, loadDemo, login, register } from '@/lib/api';
import type { AuthResponse } from '@/lib/api';

function formatSize(bytes: number) {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function getUser(): AuthResponse | null {
  try { const r = localStorage.getItem('hp-auth-user'); return r ? JSON.parse(r) : null; } catch { return null; }
}
function saveAuth(a: AuthResponse) {
  localStorage.setItem('hp-auth-token', a.token);
  localStorage.setItem('hp-auth-user', JSON.stringify(a));
}
function clearAuth() {
  localStorage.removeItem('hp-auth-token'); localStorage.removeItem('hp-auth-user');
}

export default function HomePage() {
  const router = useRouter();
  const fileInput = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState({ loaded: 0, total: 0, percent: 0 });
  const [phase, setPhase] = useState<'idle' | 'uploading' | 'processing'>('idle');
  const [error, setError] = useState('');
  const [authUser, setAuthUser] = useState<AuthResponse | null>(getUser());
  const [authMode, setAuthMode] = useState<'login' | 'register' | null>(null);
  const [authForm, setAuthForm] = useState({ username: '', password: '', email: '' });
  const [authError, setAuthError] = useState('');

  const handleAuth = async () => {
    setAuthError('');
    try {
      const fn = authMode === 'register' ? register : login;
      const result = await fn({ username: authForm.username, password: authForm.password, email: authForm.email });
      saveAuth(result);
      setAuthUser(result);
      setAuthMode(null);
      setAuthForm({ username: '', password: '', email: '' });
    } catch (e: any) { setAuthError(e.message || '认证失败'); }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) {
      if (!f.name.toLowerCase().endsWith('.zip')) { setError('仅支持 .zip 文件'); return; }
      setFile(f);
      setError('');
    }
  };

  const handleUpload = useCallback(async () => {
    if (!authUser) { setAuthMode('login'); return; }
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

  const features = [
    { icon: '📊', title: '自动解析', desc: '流式解析超大 XML，处理百万条健康记录', color: 'bg-[#007AFF]/5 ring-[#007AFF]/10' },
    { icon: '📈', title: '趋势图表', desc: '心率、睡眠、步数等核心指标日/周/月趋势', color: 'bg-[#34C759]/5 ring-[#34C759]/10' },
    { icon: '🤖', title: 'AI 深度对话', desc: '基于真实数据，DeepSeek 多轮分析健康状态', color: 'bg-[#5856D6]/5 ring-[#5856D6]/10' },
  ];

  return (
    <div className="min-h-screen bg-gradient-to-b from-[#F5F5F7] via-white to-[#F5F5F7]">
    <div className="max-w-7xl mx-auto px-6 py-16 lg:py-24 relative">
      {/* Auth bar — top right */}
      <div className="absolute top-6 right-6 flex items-center gap-3">
        {authUser ? (
          <div className="flex items-center gap-3">
            <span className="text-sm text-slate-500">{authUser.username}</span>
            <button onClick={() => { clearAuth(); setAuthUser(null); }}
              className="text-xs text-slate-400 hover:text-slate-600 transition-colors">退出</button>
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <button onClick={() => { setAuthMode('login'); setAuthError(''); }}
              className="px-4 py-2 text-sm font-medium text-slate-600 hover:text-slate-900 transition-colors">登录</button>
            <button onClick={() => { setAuthMode('register'); setAuthError(''); }}
              className="px-4 py-2 text-sm font-medium bg-[#3A7BFF] text-white rounded-full hover:bg-[#2B6AE8] transition-all">注册</button>
          </div>
        )}
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-start">

        {/* ── Left Column: Brand + Features ── */}
        <div className="lg:col-span-5 space-y-10">
          <div className="space-y-6">
            <div className="inline-flex items-center gap-2 px-4 py-1.5 bg-[#007AFF]/8 text-[#007AFF] rounded-full text-sm font-medium ring-1 ring-[#007AFF]/10">
              <span className="w-2 h-2 bg-[#007AFF] rounded-full animate-pulse" />
              基于 DeepSeek AI 深度分析
            </div>
            <h1 className="text-5xl lg:text-6xl font-extrabold tracking-tight leading-tight">
              <span className="bg-gradient-to-b from-slate-800 to-slate-500 bg-clip-text text-transparent">
                Apple Health+
              </span>
              <br />
              <span className="text-slate-700">苹果健康数据<br className="hidden sm:inline"/>可视化与 AI 洞察</span>
            </h1>
            <p className="text-lg text-slate-500 max-w-lg leading-relaxed tracking-wide">
              上传 Apple Health 导出的 导出.zip，自动生成健康指标图表、趋势分析，与 AI 深度对话你的健康数据
            </p>
          </div>

          {/* Feature Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {features.slice(0, 2).map((f) => (
              <div key={f.title} className={`${f.color} bg-white ring-1 rounded-3xl p-5 shadow-[0_8px_30px_rgb(0,0,0,0.04)] hover:-translate-y-1 hover:shadow-[0_12px_40px_rgb(0,0,0,0.06)] transition-all duration-300`}>
                <div className="text-2xl mb-2">{f.icon}</div>
                <h3 className="font-bold text-slate-800 text-sm mb-1">{f.title}</h3>
                <p className="text-xs text-slate-500 leading-relaxed">{f.desc}</p>
              </div>
            ))}
            <div key={features[2].title} className={`sm:col-span-2 ${features[2].color} bg-white ring-1 rounded-3xl p-5 shadow-[0_8px_30px_rgb(0,0,0,0.04)] hover:-translate-y-1 hover:shadow-[0_12px_40px_rgb(0,0,0,0.06)] transition-all duration-300`}>
              <div className="text-2xl mb-2">{features[2].icon}</div>
              <h3 className="font-bold text-slate-800 text-sm mb-1">{features[2].title}</h3>
              <p className="text-xs text-slate-500 leading-relaxed">{features[2].desc}</p>
            </div>
          </div>
        </div>

        {/* ── Right Column: Action Zone ── */}
        <div className="lg:col-span-7">
          <div className="bg-white rounded-3xl ring-1 ring-black/5 shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-8 lg:p-10 space-y-8">

            {/* How to Export Guide */}
            <div>
              <h3 className="text-base font-semibold text-slate-800 mb-5">如何获取导出数据？</h3>
              <div className="space-y-4">
              {[
                '打开 iPhone 上的 "健康" App',
                '点击右上角 头像（或个人资料图标）',
                '下滑到底部，点击 "导出所有健康数据"',
                '保存生成的 导出.zip 文件',
                '在此页面拖拽或选择该文件上传分析',
              ].map((text, i) => (
                <div key={i} className="flex items-start gap-4">
                  <div className="flex-shrink-0 w-8 h-8 bg-[#007AFF]/10 rounded-full flex items-center justify-center text-[#007AFF] text-sm font-bold">{i + 1}</div>
                  <p className="text-sm text-slate-600 pt-1">{text}</p>
                </div>
              ))}
              </div>
            </div>

            <div className="border-t border-slate-100" />

            {/* Upload Zone */}
            <div
              onDrop={handleDrop}
              onDragOver={(e) => e.preventDefault()}
              className={`rounded-2xl p-8 text-center transition-all duration-500 ${
                uploading
                  ? 'bg-gradient-to-br from-[#F8F8FC] to-[#EEF0FF] ring-1 ring-[#007AFF]/20'
                  : 'bg-slate-50/50 border-2 border-dashed border-slate-200 hover:border-slate-300 hover:bg-blue-50/30'
              }`}
            >
              {uploading ? (
                <div className="space-y-4">
                  <div className="w-14 h-14 mx-auto bg-[#007AFF]/10 rounded-2xl flex items-center justify-center text-2xl animate-pulse">
                    {phase === 'uploading' ? '📤' : '⏳'}
                  </div>
                  <p className="text-slate-800 font-semibold">
                    {phase === 'uploading' ? `上传中 ${progress.percent}%` : '正在提交任务...'}
                  </p>
                  {phase === 'uploading' && (
                    <>
                      <p className="text-sm text-slate-500">{formatSize(progress.loaded)} / {formatSize(progress.total)}</p>
                      <div className="w-full bg-slate-200 rounded-full h-2 overflow-hidden">
                        <div className="h-full rounded-full bg-[#007AFF] transition-all duration-300" style={{ width: `${progress.percent}%` }} />
                      </div>
                    </>
                  )}
                  {phase === 'processing' && (
                    <div className="w-full bg-slate-200 rounded-full h-2 overflow-hidden">
                      <div className="h-full w-full rounded-full bg-[#007AFF] animate-shimmer" style={{ backgroundSize: '200% 100%' }} />
                    </div>
                  )}
                </div>
              ) : !file ? (
                <div className="space-y-4">
                  <div className="w-14 h-14 mx-auto bg-[#007AFF]/10 rounded-2xl flex items-center justify-center text-2xl">📁</div>
                  <div>
                    <p className="text-slate-700 font-semibold text-lg">拖拽 导出.zip 到此处</p>
                    <p className="text-sm text-slate-400 mt-1">或点击下方按钮选择文件</p>
                  </div>
                  <input ref={fileInput} type="file" accept=".zip" onChange={handleFileChange} className="hidden" />
                  <button onClick={() => fileInput.current?.click()}
                    className="px-8 py-3 bg-[#007AFF] text-white rounded-full hover:bg-[#0077EE] hover:-translate-y-0.5 hover:shadow-md transition-all duration-300 font-semibold shadow-[0_4px_14px_rgba(0,122,255,0.3)]">
                    选择文件
                  </button>
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="w-14 h-14 mx-auto bg-[#34C759]/10 rounded-2xl flex items-center justify-center text-2xl">✅</div>
                  <p className="text-slate-800 font-semibold truncate text-lg">{file.name}</p>
                  <p className="text-sm text-slate-500">{formatSize(file.size)}</p>
                  <button onClick={handleUpload}
                    className="w-full py-3.5 bg-[#007AFF] text-white rounded-full hover:bg-[#0077EE] hover:-translate-y-0.5 hover:shadow-md transition-all duration-300 text-lg font-bold shadow-[0_4px_14px_rgba(0,122,255,0.3)]">
                    开始分析
                  </button>
                  <button onClick={() => { setFile(null); setError(''); }} className="text-sm text-slate-400 hover:text-slate-600 transition-colors">重新选择</button>
                </div>
              )}
              {error && (
                <p className="mt-4 text-sm text-[#FF3B30] bg-[#FF3B30]/5 rounded-2xl px-4 py-3 ring-1 ring-[#FF3B30]/10">{error}</p>
              )}
            </div>

            {/* Demo Preview */}
            <div className="text-center pt-2">
              <button onClick={handleDemo} disabled={uploading}
                className="px-6 py-2.5 bg-white ring-1 ring-[#5856D6]/15 text-[#5856D6] rounded-full hover:bg-[#5856D6]/5 hover:-translate-y-0.5 hover:shadow-md disabled:opacity-40 transition-all duration-300 text-sm font-medium shadow-[0_2px_8px_rgba(0,0,0,0.04)]">
                🚀 快速预览示例数据
              </button>
            </div>

          </div>
        </div>

      </div>

      <p className="text-center text-sm text-slate-400 max-w-md mx-auto mt-16 tracking-wide">
        你的健康数据仅用于本次分析，不会与第三方共享。本产品不提供医疗诊断，AI 分析仅供参考。
      </p>

      {/* ── Auth Modal ── */}
      {authMode && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm" onClick={() => setAuthMode(null)}>
          <div className="bg-white rounded-3xl shadow-[0_20px_60px_rgba(0,0,0,0.12)] p-8 w-full max-w-sm mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-xl font-bold text-slate-900 mb-6">{authMode === 'login' ? '登录' : '注册'}</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">用户名</label>
                <input value={authForm.username} onChange={e => setAuthForm({...authForm, username: e.target.value})}
                  className="w-full px-4 py-3 bg-slate-50 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#3A7BFF]/30" placeholder="输入用户名" />
              </div>
              {authMode === 'register' && (
                <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">邮箱</label>
                  <input value={authForm.email} onChange={e => setAuthForm({...authForm, email: e.target.value})}
                    className="w-full px-4 py-3 bg-slate-50 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#3A7BFF]/30" placeholder="your@email.com" />
                </div>
              )}
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-1">密码</label>
                <input type="password" value={authForm.password} onChange={e => setAuthForm({...authForm, password: e.target.value})}
                  onKeyDown={e => e.key === 'Enter' && handleAuth()}
                  className="w-full px-4 py-3 bg-slate-50 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#3A7BFF]/30" placeholder="输入密码" />
              </div>
              {authError && <p className="text-xs text-red-500">{authError}</p>}
              <button onClick={handleAuth}
                className="w-full py-3 bg-[#3A7BFF] text-white rounded-full font-semibold hover:bg-[#2B6AE8] transition-all">
                {authMode === 'login' ? '登录' : '注册'}
              </button>
              <p className="text-center text-xs text-slate-400">
                {authMode === 'login' ? '没有账号？' : '已有账号？'}
                <button onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setAuthError(''); }}
                  className="text-[#3A7BFF] font-medium ml-1"> {authMode === 'login' ? '注册' : '登录'}</button>
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
    </div>
  );
}
