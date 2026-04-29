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
    <div className="space-y-6">
      <button onClick={() => router.push(`/reports/${reportId}/overview`)}
        className="inline-flex items-center gap-1 text-blue-600 hover:text-blue-800 text-sm font-medium">← 返回概览</button>

      {/* Health Reference Card */}
      {ref && (
        <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-2xl border border-blue-100 p-5">
          <h3 className="font-bold text-blue-900 mb-2">📖 关于{label}</h3>
          <p className="text-sm text-blue-800 leading-relaxed mb-2">{ref.description}</p>
          <div className="flex gap-4 text-xs mt-3">
            <div className="flex-1 bg-white/70 rounded-xl p-3">
              <span className="font-semibold text-emerald-700">健康范围</span>
              <p className="text-gray-600 mt-0.5 leading-relaxed">{ref.healthyRange}</p>
            </div>
            <div className="flex-1 bg-white/70 rounded-xl p-3">
              <span className="font-semibold text-amber-700">健康建议</span>
              <p className="text-gray-600 mt-0.5 leading-relaxed">{ref.advice}</p>
            </div>
          </div>
        </div>
      )}

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div><h2 className="text-3xl font-extrabold text-gray-900">{label}</h2><p className="text-gray-400 text-sm mt-0.5">{unit}</p></div>
        <button onClick={() => router.push(`/reports/${reportId}/chat`)}
          className="self-start px-5 py-2.5 bg-blue-600 text-white rounded-xl hover:bg-blue-700 text-sm font-semibold shadow-lg">🤖 问 AI</button>
      </div>

      <div className="flex gap-1.5 bg-gray-100 rounded-xl p-1 w-fit">
        {(['DAILY','WEEKLY','MONTHLY'] as const).map(g => (
          <button key={g} onClick={() => setGranularity(g)}
            className={`px-5 py-2 rounded-lg text-sm font-medium transition-all ${granularity===g?'bg-white text-gray-900 shadow-sm':'text-gray-500'}`}>
            {{DAILY:'日',WEEKLY:'周',MONTHLY:'月'}[g]}</button>
        ))}
      </div>

      <select value={compare} onChange={e => setCompare(e.target.value)}
        className="text-xs border border-gray-200 rounded-lg px-2 py-1.5 w-40 focus:ring-1 focus:ring-blue-500">
        <option value="">不对比</option>
        {COMPARE_OPTIONS.filter(m => m !== metricKey).map(m => <option key={m} value={m}>{METRIC_LABELS[m] || m}</option>)}
      </select>

      {error && <div className="bg-red-50 rounded-xl p-4 text-red-600 text-sm">{error}</div>}
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
                <div className="text-xs text-gray-400 uppercase">{s.label}</div>
                <div className="text-2xl font-extrabold text-gray-900 mt-1">{s.value.toFixed(1)}</div>
                <div className="text-xs text-gray-400 mt-0.5">{unit}</div>
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
