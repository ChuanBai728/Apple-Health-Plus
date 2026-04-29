'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { getUploadStatus, deleteUpload } from '@/lib/api';
import { UploadStatusResponse, STATUS_LABELS } from '@/lib/types';

const STATUS_ICONS: Record<string, string> = {
  created: '📋', uploading: '📤', uploaded: '✅',
  queued: '⏳', parsing: '🔍', parsed: '📊',
  aggregating: '🧮', ready: '🎉', failed: '❌',
};

const PHASES = [
  { key: 'uploaded', label: '文件上传', icon: '📤' },
  { key: 'queued', label: '任务排队', icon: '⏳' },
  { key: 'parsing', label: '解析数据', icon: '🔍' },
  { key: 'parsed', label: '解析完成', icon: '📊' },
  { key: 'aggregating', label: '聚合指标', icon: '🧮' },
  { key: 'ready', label: '分析完成', icon: '🎉' },
];

const PHASE_ORDER = ['uploaded', 'queued', 'parsing', 'parsed', 'aggregating', 'ready'];

function getPhaseIndex(status: string): number {
  const idx = PHASE_ORDER.indexOf(status);
  if (idx >= 0) return idx;
  if (status === 'created' || status === 'uploading') return 0;
  return -1;
}

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  if (m > 0) return `${m}分${s}秒`;
  return `${s}秒`;
}

export default function UploadProgressPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [status, setStatus] = useState<UploadStatusResponse | null>(null);
  const [error, setError] = useState('');
  const [elapsed, setElapsed] = useState(0);
  const startTime = useRef(Date.now());

  const poll = useCallback(async () => {
    try {
      const s = await getUploadStatus(id);
      setStatus(s);
      if (s.status === 'ready') {
        setTimeout(() => router.push(`/reports/${id}/overview`), 1200);
      } else if (s.status === 'failed') {
        setError(s.lastError || '处理失败');
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '获取状态失败');
    }
  }, [id, router]);

  useEffect(() => {
    poll();
    const timer = setInterval(() => {
      setElapsed(Math.floor((Date.now() - startTime.current) / 1000));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (status?.status !== 'ready' && status?.status !== 'failed') {
      const pollTimer = setInterval(poll, 2000);
      return () => clearInterval(pollTimer);
    }
  }, [poll, status?.status]);

  const handleDelete = async () => {
    try {
      await deleteUpload(id);
      router.push('/');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '删除失败');
    }
  };

  if (!status) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="animate-spin h-10 w-10 border-4 border-blue-600 border-t-transparent rounded-full" />
        <span className="ml-4 text-[#8E8E93] text-lg">加载中...</span>
      </div>
    );
  }

  const isRunning = !['ready', 'failed', 'deleted'].includes(status.status);
  const currentPhase = getPhaseIndex(status.status);
  const progress = {
    created: 5, uploading: 15, uploaded: 20, queued: 30,
    parsing: 50, parsed: 70, aggregating: 85, ready: 100, failed: 0,
  }[status.status] || 0;

  return (
    <div className="max-w-lg mx-auto space-y-6 pt-8">
      <div className="text-center">
        <div className="text-5xl mb-4">{STATUS_ICONS[status.status] || '⏳'}</div>
        <h2 className="text-2xl font-bold text-[#1C1C1E]">
          {status.status === 'ready'
            ? '处理完成！'
            : status.status === 'failed'
            ? '处理失败'
            : '正在分析数据'}
        </h2>
        {isRunning && (
          <p className="text-[#8E8E93]/70 text-sm mt-1">
            已耗时 {formatTime(elapsed)}
          </p>
        )}
      </div>

      {/* Phase steps */}
      <div className="space-y-1.5">
        {PHASES.map((phase, i) => {
          const done = currentPhase >= i;
          const active = currentPhase === i;
          const pending = currentPhase < i;
          return (
            <div
              key={phase.key}
              className={`flex items-center gap-3 px-4 py-2.5 rounded-xl transition-colors ${
                active ? 'bg-blue-50 border border-blue-200' :
                done ? 'bg-green-50/50' : 'bg-gray-50'
              }`}
            >
              <span className={`text-lg ${pending ? 'opacity-30' : ''}`}>
                {done ? '✅' : phase.icon}
              </span>
              <span className={`text-sm font-medium flex-1 ${
                active ? 'text-blue-700' : done ? 'text-green-700' : 'text-[#8E8E93]/70'
              }`}>
                {phase.label}
              </span>
              {active && (
                <span className="inline-flex items-center gap-1.5 text-xs text-blue-500 font-medium">
                  <span className="w-1.5 h-1.5 bg-blue-500 rounded-full animate-pulse" />
                  进行中
                </span>
              )}
              {done && !active && (
                <span className="text-xs text-green-500">✓</span>
              )}
            </div>
          );
        })}
      </div>

      {/* Progress bar with shimmer */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5 space-y-3">
        <div className="flex justify-between text-sm">
          <span className="text-[#8E8E93] truncate max-w-[200px]">{status.fileName}</span>
          <span className="text-[#8E8E93]/70">{progress}%</span>
        </div>
        <div className="w-full bg-gray-100 rounded-full h-2.5 overflow-hidden">
          {isRunning ? (
            <div className="h-full w-full relative overflow-hidden rounded-full">
              <div
                className="absolute inset-0 bg-gradient-to-r from-blue-400 via-indigo-500 to-blue-400 animate-shimmer"
                style={{
                  backgroundSize: '200% 100%',
                  animation: 'shimmer 2s ease-in-out infinite',
                }}
              />
            </div>
          ) : (
            <div
              className={`h-full rounded-full transition-all duration-1000 ${
                status.status === 'failed'
                  ? 'bg-red-500'
                  : 'bg-green-500'
              }`}
              style={{ width: `${progress}%` }}
            />
          )}
        </div>
        {isRunning && (
          <p className="text-xs text-[#8E8E93]/70 text-center">
            大文件解析需要一些时间，请耐心等待...
          </p>
        )}
      </div>

      {/* Ready */}
      {status.status === 'ready' && (
        <div className="bg-gradient-to-br from-green-50 to-emerald-50 border border-green-200 rounded-2xl p-6 text-center space-y-4">
          <p className="text-green-800 font-bold text-lg">数据已就绪</p>
          <p className="text-green-600 text-sm">
            健康指标已完成解析和聚合，耗时 {formatTime(elapsed)}
          </p>
          <button
            onClick={() => router.push(`/reports/${id}/overview`)}
            className="w-full py-3 bg-green-600 text-white rounded-xl hover:bg-green-700 font-bold transition-colors shadow-lg shadow-green-200"
          >
            查看健康报告
          </button>
        </div>
      )}

      {/* Failed */}
      {status.status === 'failed' && (
        <div className="bg-gradient-to-br from-red-50 to-rose-50 border border-red-200 rounded-2xl p-6 space-y-4">
          <p className="text-red-800 font-bold">处理失败</p>
          {error && <p className="text-sm text-red-600 bg-white/50 rounded-lg p-3">{error}</p>}
          <div className="flex gap-3">
            <button onClick={() => router.push('/')} className="flex-1 py-2.5 bg-gray-800 text-white rounded-xl font-medium">
              重新上传
            </button>
            <button onClick={handleDelete} className="px-4 py-2.5 text-red-600 hover:bg-red-100 rounded-xl transition-colors">
              删除记录
            </button>
          </div>
        </div>
      )}

      {status.status === 'ready' && (
        <button onClick={handleDelete} className="w-full text-sm text-[#8E8E93]/70 hover:text-red-500 transition-colors py-2">
          删除此上传数据
        </button>
      )}
    </div>
  );
}
