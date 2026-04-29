'use client';

import { useEffect, useState, Suspense } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { getMetricSeries, getDualMetricSeries } from '@/lib/api';
import { MetricSeriesResponse, METRIC_LABELS, METRIC_UNITS, METRIC_REFERENCE } from '@/lib/types';
import { TrendChart } from '@/components/TrendChart';
import Link from 'next/link';

const STAT_COLORS = ['border-l-blue-500','border-l-emerald-500','border-l-amber-500','border-l-purple-500'];
const COMPARE_OPTIONS = ['step_count','active_energy_burned','heart_rate','resting_heart_rate','body_mass','sleep_duration','workout','flights_climbed','vo2max','oxygen_saturation'];

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

  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <button onClick={() => router.push(`/reports/${reportId}/overview`)}
        className="inline-flex items-center gap-1 text-[#007AFF] hover:underline text-sm font-medium">← 返回概览</button>

      {/* Anomaly Alert */}
      {data && data.points.length > 0 && data.points[data.points.length - 1].anomaly && (() => {
        const lp = data.points[data.points.length - 1];
        return (
        <div className="bg-[#FFFBF5] border border-[#FF9500]/20 rounded-2xl p-5 space-y-2">
          <h3 className="text-sm font-bold text-[#FF9500]">⚠ 检测到异常波动</h3>
          <p className="text-sm text-[#3A3A3C] leading-relaxed">
            最新值 {lp.value?.toFixed(1)}{unit}
            {lp.baselineAvg30d != null ? `，偏离 30 天基线 ${lp.baselineAvg30d.toFixed(1)}${unit}` : ''}
            （超过 2.5 倍标准差）
          </p>
          <div className="flex gap-4 text-sm text-[#8E8E93]">
            {lp.trendDelta7d != null && (
              <span>较 7 天前: <span className={lp.trendDelta7d > 0 ? 'text-[#FF3B30]' : 'text-[#34C759]'}>
                {lp.trendDelta7d > 0 ? '+' : ''}{lp.trendDelta7d.toFixed(1)}{unit}</span></span>
            )}
            {lp.trendDelta30d != null && (
              <span>较 30 天前: <span className={lp.trendDelta30d > 0 ? 'text-[#FF3B30]' : 'text-[#34C759]'}>
                {lp.trendDelta30d > 0 ? '+' : ''}{lp.trendDelta30d.toFixed(1)}{unit}</span></span>
            )}
          </div>
        </div>
      )})()}

      {/* Health Reference Card */}
      {ref && (
        <div className="bg-gradient-to-br from-[#007AFF]/5 to-[#5856D6]/5 rounded-2xl border border-[#007AFF]/10 p-5">
          <h3 className="font-bold text-blue-900 mb-2">📖 关于{label}</h3>
          <p className="text-sm text-blue-800 leading-relaxed mb-2">{ref.description}</p>
          <div className="flex gap-4 text-xs mt-3">
            <div className="flex-1 bg-white/70 rounded-xl p-3">
              <span className="font-semibold text-emerald-700">健康范围</span>
              <p className="text-[#3A3A3C] mt-0.5 leading-relaxed">{ref.healthyRange}</p>
            </div>
            <div className="flex-1 bg-white/70 rounded-xl p-3">
              <span className="font-semibold text-amber-700">健康建议</span>
              <p className="text-[#3A3A3C] mt-0.5 leading-relaxed">{ref.advice}</p>
            </div>
          </div>
        </div>
      )}

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div><h2 className="text-3xl font-extrabold text-[#1C1C1E]">{label}</h2><p className="text-gray-400 text-sm mt-0.5">{unit}</p></div>
        <button onClick={() => router.push(`/reports/${reportId}/chat`)}
          className="self-start px-5 py-2.5 bg-[#007AFF] text-white rounded-full hover:bg-[#0077EE] text-sm font-semibold shadow-lg">🤖 问 AI</button>
      </div>

      <div className="flex gap-1.5 bg-gray-100 rounded-xl p-1 w-fit">
        {(['DAILY','WEEKLY','MONTHLY'] as const).map(g => (
          <button key={g} onClick={() => setGranularity(g)}
            className={`px-5 py-2 rounded-lg text-sm font-medium transition-all ${granularity===g?'bg-white text-[#1C1C1E] shadow-sm':'text-gray-500'}`}>
            {{DAILY:'日',WEEKLY:'周',MONTHLY:'月'}[g]}</button>
        ))}
      </div>

      <select value={compare} onChange={e => setCompare(e.target.value)}
        className="text-sm border border-gray-200 rounded-lg px-2 py-1.5 w-40 focus:ring-1 focus:ring-blue-500">
        <option value="">不对比</option>
        {COMPARE_OPTIONS.filter(m => m !== metricKey).map(m => <option key={m} value={m}>{METRIC_LABELS[m] || m}</option>)}
      </select>

      {error && <div className="bg-[#FF3B30]/5 rounded-2xl p-4 text-[#FF3B30] text-sm">{error}</div>}
      {data && <TrendChart data={data.points} label={data.label || label} />}

      {data && data.points.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {(() => {
            const values = data.points.map(p => p.value).filter((v): v is number => v != null);
            if (values.length === 0) return null;
            const avg = values.reduce((a,b)=>a+b,0)/values.length;
            return [
              {label:'最新',value:values[values.length-1]},
              {label:'平均',value:avg},{label:'最高',value:Math.max(...values)},{label:'最低',value:Math.min(...values)}
            ].map((s,i) => (
              <div key={s.label} className={`bg-white rounded-xl border border-gray-200 border-l-4 ${STAT_COLORS[i]} p-4`}>
                <div className="text-sm text-[#8E8E93] uppercase font-medium">{s.label}</div>
                <div className="text-2xl font-extrabold text-[#1C1C1E] mt-1">{s.value.toFixed(1)}</div>
                <div className="text-sm text-[#8E8E93] mt-0.5">{unit}</div>
              </div>
            ));
          })()}
        </div>
      )}
      <Link href={`/reports/${reportId}/heatmap`} className="block text-center text-sm text-gray-400 hover:text-blue-600 py-2">🗓 查看热力图</Link>
    </div>
  );
}

export default function MetricDetailPage() {
  return <Suspense fallback={<div className="py-20 text-center text-gray-400">加载中...</div>}><MetricContent /></Suspense>;
}
