'use client';

import { useEffect, useState, Suspense } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { getMetricSeries, getDualMetricSeries } from '@/lib/api';
import { MetricSeriesResponse, METRIC_LABELS, METRIC_UNITS, METRIC_REFERENCE } from '@/lib/types';
import { TrendChart } from '@/components/TrendChart';
import Link from 'next/link';

const COMPARE_OPTIONS = ['step_count','active_energy_burned','heart_rate','resting_heart_rate','body_mass','sleep_duration','workout','flights_climbed','vo2max','oxygen_saturation'];
const STAT_COLORS = ['#3A7BFF','#34C759','#FF9F0A','#AF52DE'];

function MetricContent() {
  const { reportId, metricKey } = useParams<{ reportId: string; metricKey: string }>();
  const router = useRouter();
  const params = useSearchParams();
  const [granularity, setGranularity] = useState<'DAILY' | 'WEEKLY' | 'MONTHLY'>('DAILY');
  const [data, setData] = useState<MetricSeriesResponse | null>(null);
  const [error, setError] = useState('');
  const [compare, setCompare] = useState(params.get('compare') || '');

  useEffect(() => {
    setError('');
    const p = compare
      ? getDualMetricSeries(reportId, metricKey, compare, granularity)
      : getMetricSeries(reportId, metricKey, granularity);
    p.then(setData).catch((e) => setError(e.message));
  }, [reportId, metricKey, granularity, compare]);

  const label = METRIC_LABELS[metricKey] || metricKey;
  const unit = METRIC_UNITS[metricKey] || '';
  const ref = METRIC_REFERENCE[metricKey];

  const stats = (() => {
    if (!data) return null;
    const values = data.points.map(p => p.value).filter((v): v is number => v != null);
    if (values.length === 0) return null;
    const avg = values.reduce((a,b)=>a+b,0)/values.length;
    const max = Math.max(...values);
    const min = Math.min(...values);
    const latest = values[values.length - 1];
    return [
      { label: '最新', value: latest, icon: '⏱', color: STAT_COLORS[0] },
      { label: '平均', value: avg, icon: '📊', color: STAT_COLORS[1] },
      { label: '最高', value: max, icon: '⬆', color: STAT_COLORS[2] },
      { label: '最低', value: min, icon: '⬇', color: STAT_COLORS[3] },
    ];
  })();

  const lastPt = data?.points?.[data.points.length - 1];
  const hasAnomaly = lastPt?.anomaly === true;

  return (
    <div className="space-y-6 max-w-4xl mx-auto pb-12 px-4">
      {/* Back */}
      <button onClick={() => router.push(`/reports/${reportId}/overview`)}
        className="inline-flex items-center gap-1.5 text-[#3A7BFF] hover:text-[#2B6AE8] text-sm font-medium transition-colors">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><polyline points="15 18 9 12 15 6"/></svg>
        返回概览
      </button>

      {/* ── Anomaly Alert ── */}
      {hasAnomaly && lastPt && (
        <div className="bg-[#FFF8F0] border border-[#FF9F0A]/15 rounded-2xl p-5">
          <div className="flex items-start gap-3">
            <span className="text-xl shrink-0">⚠️</span>
            <div className="space-y-2">
              <h3 className="text-sm font-bold text-[#FF9F0A]">检测到异常波动</h3>
              <p className="text-sm text-[#6E6E73] leading-relaxed">
                最新值 <span className="font-semibold text-[#1D1D1F]">{lastPt.value?.toFixed(1)}{unit}</span>
                {lastPt.baselineAvg30d != null ? `，偏离 30 天基线 ${lastPt.baselineAvg30d.toFixed(1)}${unit}` : ''}
                （超过 2.5 倍标准差）
              </p>
              <div className="flex gap-4 text-xs text-[#6E6E73]">
                {lastPt.trendDelta7d != null && (
                  <span>较7天前 <span className={lastPt.trendDelta7d > 0 ? 'text-[#FF3B30] font-medium' : 'text-[#34C759] font-medium'}>{lastPt.trendDelta7d > 0 ? '+' : ''}{lastPt.trendDelta7d.toFixed(1)}{unit}</span></span>
                )}
                {lastPt.trendDelta30d != null && (
                  <span>较30天前 <span className={lastPt.trendDelta30d > 0 ? 'text-[#FF3B30] font-medium' : 'text-[#34C759] font-medium'}>{lastPt.trendDelta30d > 0 ? '+' : ''}{lastPt.trendDelta30d.toFixed(1)}{unit}</span></span>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Health Info Card ── */}
      {ref && (
        <div className="bg-gradient-to-r from-[#3A7BFF]/5 to-[#3A7BFF]/2 rounded-2xl p-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            <div className="md:col-span-1">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-lg">📊</span>
                <h3 className="font-bold text-[#1D1D1F] text-sm">关于{label}</h3>
              </div>
              <p className="text-sm text-[#6E6E73] leading-relaxed">{ref.description}</p>
            </div>
            <div className="bg-white/80 rounded-xl p-4">
              <div className="flex items-center gap-1.5 mb-1.5">
                <span className="text-xs">✅</span>
                <span className="font-semibold text-[#34C759] text-xs">健康范围</span>
              </div>
              <p className="text-sm text-[#1D1D1F] leading-relaxed">{ref.healthyRange}</p>
            </div>
            <div className="bg-white/80 rounded-xl p-4">
              <div className="flex items-center gap-1.5 mb-1.5">
                <span className="text-xs">💡</span>
                <span className="font-semibold text-[#FF9F0A] text-xs">健康建议</span>
              </div>
              <p className="text-sm text-[#1D1D1F] leading-relaxed">{ref.advice}</p>
            </div>
          </div>
        </div>
      )}

      {/* ── Title Row ── */}
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-[28px] font-bold text-[#1D1D1F] tracking-tight">{label}</h1>
          <p className="text-sm text-[#6E6E73] mt-0.5">{unit}</p>
        </div>
        <button onClick={() => router.push(`/reports/${reportId}/chat`)}
          className="fixed bottom-8 right-8 z-50 px-5 py-3 bg-[#3A7BFF] text-white rounded-full shadow-[0_8px_24px_rgba(58,123,255,0.35)] hover:bg-[#2B6AE8] hover:scale-105 transition-all duration-300 text-sm font-semibold flex items-center gap-2">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
          问 AI
        </button>
      </div>

      {/* ── Time Selector + Compare ── */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex gap-0.5 bg-[#F0F2F5] rounded-xl p-1">
          {(['DAILY','WEEKLY','MONTHLY'] as const).map(g => (
            <button key={g} onClick={() => setGranularity(g)}
              className={`px-5 py-2 rounded-[10px] text-sm font-medium transition-all duration-300 ${
                granularity===g ? 'bg-white text-[#1D1D1F] shadow-sm' : 'text-[#6E6E73] hover:text-[#1D1D1F]'}`}>
              {{DAILY:'日',WEEKLY:'周',MONTHLY:'月'}[g]}
            </button>
          ))}
        </div>

        <div className="relative">
          <select value={compare} onChange={e => setCompare(e.target.value)}
            className="appearance-none pl-3 pr-8 py-2 bg-white border border-[#E5E7EB] rounded-xl text-sm text-[#1D1D1F] focus:outline-none focus:ring-2 focus:ring-[#3A7BFF]/30 focus:border-[#3A7BFF]/30 cursor-pointer">
            <option value="">不对比</option>
            {COMPARE_OPTIONS.filter(m => m !== metricKey).map(m => <option key={m} value={m}>{METRIC_LABELS[m] || m}</option>)}
          </select>
          <svg className="absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#6E6E73" strokeWidth="2.5" strokeLinecap="round"><polyline points="6 9 12 15 18 9"/></svg>
        </div>
      </div>

      {/* ── Error ── */}
      {error && <div className="bg-[#FF3B30]/5 rounded-2xl p-4 text-[#FF3B30] text-sm">{error}</div>}

      {/* ── Chart ── */}
      {data && (
        <div className="bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] p-5 transition-all duration-300">
          <TrendChart data={data.points} label={data.label || label} height={280} />
        </div>
      )}

      {/* ── Stats Row ── */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {stats.map((s) => (
            <div key={s.label}
              className="group bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] hover:-translate-y-0.5 hover:shadow-[0_8px_24px_rgba(0,0,0,0.06)] transition-all duration-300 overflow-hidden">
              <div className="h-1" style={{backgroundColor: s.color}} />
              <div className="p-4">
                <div className="flex items-center gap-1.5 mb-2">
                  <span className="text-sm">{s.icon}</span>
                  <span className="text-xs text-[#6E6E73] font-medium uppercase tracking-wide">{s.label}</span>
                </div>
                <div className="text-2xl font-bold text-[#1D1D1F] tabular-nums">{Number.isInteger(s.value) ? s.value : s.value.toFixed(1)}</div>
                <div className="text-xs text-[#6E6E73]/70 mt-0.5">{unit}</div>
              </div>
            </div>
          ))}
        </div>
      )}

      <Link href={`/reports/${reportId}/heatmap`}
        className="block text-center text-sm text-[#6E6E73] hover:text-[#3A7BFF] py-2 transition-colors">🗓 查看热力图</Link>
    </div>
  );
}

export default function MetricDetailPage() {
  return <Suspense fallback={<div className="py-20 text-center text-[#6E6E73]">加载中...</div>}><MetricContent /></Suspense>;
}
