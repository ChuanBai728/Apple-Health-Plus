'use client';

import { useEffect, useState, useMemo } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { getMetricSeries } from '@/lib/api';
import { METRIC_LABELS } from '@/lib/types';
import Link from 'next/link';

const METRICS = ['step_count','active_energy_burned','workout','heart_rate','resting_heart_rate'];

export default function HeatmapPage() {
  const { reportId } = useParams<{ reportId: string }>();
  const router = useRouter();
  const [metric, setMetric] = useState('step_count');
  const [data, setData] = useState<{ date: string; value: number | null }[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getMetricSeries(reportId, metric, 'DAILY').then(r => {
      setData(r.points.map(p => ({ date: p.date, value: p.value })));
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [reportId, metric]);

  const maxVal = useMemo(() => {
    const vals = data.map(d => d.value).filter((v): v is number => v != null);
    return vals.length > 0 ? Math.max(...vals) : 1;
  }, [data]);

  const intensity = (v: number | null) => {
    if (v == null || maxVal === 0) return 'bg-gray-100';
    const p = v / maxVal;
    if (p > 0.75) return 'bg-green-700';
    if (p > 0.5) return 'bg-green-500';
    if (p > 0.25) return 'bg-green-300';
    return 'bg-green-100';
  };

  return (
    <div className="space-y-6">
      <button onClick={() => router.push(`/reports/${reportId}/overview`)} className="text-[#007AFF] hover:underline text-sm">← 返回概览</button>
      <h2 className="text-2xl font-bold">🗓 活动热力图</h2>

      <div className="flex gap-2 flex-wrap">
        {METRICS.map(m => (
          <button key={m} onClick={() => setMetric(m)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              metric === m ? 'bg-[#1C1C1E] text-white' : 'bg-[#F2F2F7] text-[#8E8E93] hover:bg-black/[0.04]'
            }`}>{METRIC_LABELS[m] || m}</button>
        ))}
      </div>

      {loading ? (
        <div className="text-center py-12 text-gray-400">加载中...</div>
      ) : (
        <div className="bg-white/70 backdrop-blur-xl rounded-2xl border border-black/5 p-4">
          <div className="grid grid-cols-[repeat(auto-fill,minmax(14px,1fr))] gap-[2px]">
            {data.map((d, i) => (
              <div key={i}
                className={`aspect-square rounded-sm ${intensity(d.value)}`}
                title={`${d.date?.substring(0,10)}: ${d.value?.toFixed(1) || '-'}`}
              />
            ))}
          </div>
          <div className="flex items-center justify-end gap-1 mt-3 text-[10px] text-gray-400">
            <span>少</span>
            <span className="w-3 h-3 rounded-sm bg-green-100" />
            <span className="w-3 h-3 rounded-sm bg-green-300" />
            <span className="w-3 h-3 rounded-sm bg-green-500" />
            <span className="w-3 h-3 rounded-sm bg-green-700" />
            <span>多</span>
          </div>
        </div>
      )}
    </div>
  );
}
