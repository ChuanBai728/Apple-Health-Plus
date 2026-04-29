'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { getInsight } from '@/lib/api';
import { METRIC_LABELS, METRIC_UNITS } from '@/lib/types';
import Link from 'next/link';

const SECTION_TITLES = /^(整体评估|关键发现|风险提示|可执行建议|核心指标|健康总结)/;

export default function ReportPage() {
  const { reportId } = useParams<{ reportId: string }>();
  const router = useRouter();
  const [type, setType] = useState<'weekly'|'monthly'>('weekly');
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    getInsight(reportId, type).then(d => { setData(d); setLoading(false); }).catch(() => setLoading(false));
  }, [reportId, type]);

  if (loading) return (
    <div className="flex items-center justify-center py-20">
      <div className="animate-spin h-8 w-8 border-4 border-[#007AFF] border-t-transparent rounded-full" />
      <span className="ml-3 text-[#8E8E93]">生成报告中...</span>
    </div>
  );
  if (!data) return <div className="text-center py-20 text-[#8E8E93]/70">暂无报告数据</div>;

  return (
    <div className="max-w-2xl mx-auto pb-10">
      <div className="flex items-center justify-between mb-6">
        <Link href={`/reports/${reportId}/overview`} className="text-[#007AFF] hover:underline text-sm">← 概览</Link>
        <div className="flex gap-1 bg-[#F2F2F7] rounded-xl p-0.5">
          <button onClick={() => setType('weekly')} className={`px-3 py-1 text-xs rounded-md font-medium transition ${type==='weekly'?'bg-white shadow-sm text-[#1C1C1E]':'text-[#8E8E93]'}`}>周报</button>
          <button onClick={() => setType('monthly')} className={`px-3 py-1 text-xs rounded-md font-medium transition ${type==='monthly'?'bg-white shadow-sm text-[#1C1C1E]':'text-[#8E8E93]'}`}>月报</button>
        </div>
        <div className="w-10" />
      </div>

      {/* Cover */}
      <div className="rounded-3xl bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF] border border-black/5 p-8 text-center mb-5">
        <div className="text-6xl mb-4">📊</div>
        <h2 className="text-2xl font-extrabold text-[#1C1C1E] mb-2">{type==='weekly'?'健康周报':'健康月报'}</h2>
        <p className="text-sm text-[#8E8E93]">{data.startDate} ~ {data.endDate}</p>
        <p className="text-xs text-[#8E8E93]/70 mt-2">共 {Object.values(data.stateDistribution).reduce((a:any,b:any)=>a+b,0) as number} 天数据</p>
      </div>

      {/* Metric Highlights */}
      <div className="bg-white/70 backdrop-blur-xl rounded-3xl border border-black/5 p-6 mb-5">
        <h3 className="text-lg font-extrabold text-[#1C1C1E] mb-4">📈 核心指标变化</h3>
        <div className="space-y-2">
          {data.highlights.map((h: any) => {
            const up = h.changePct > 0;
            const label = METRIC_LABELS[h.metricKey] || h.metricKey;
            const unit = METRIC_UNITS[h.metricKey] || '';
            return (
              <div key={h.metricKey} className="flex items-center gap-3 bg-[#F2F2F7] rounded-xl px-4 py-2.5">
                <span className="text-sm font-medium text-[#3A3A3C] flex-1">{label}</span>
                <span className="text-sm font-bold">{h.weeklyAvg.toFixed(1)}{unit}</span>
                <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${up?'bg-[#34C759]/10 text-[#34C759]':'bg-[#FF3B30]/10 text-[#FF3B30]'}`}>
                  {h.trend} {Math.abs(h.changePct).toFixed(0)}%
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* AI Narrative */}
      <div className="bg-gradient-to-br from-[#FAFAFE] to-[#F4F4FC] rounded-3xl border border-black/5 p-6 text-[#1C1C1E]">
        <div className="flex items-center gap-2 mb-4">
          <span className="text-xl">🤖</span>
          <h3 className="text-lg font-extrabold">AI {type==='weekly'?'周':'月'}度洞察</h3>
        </div>
        <div className="text-sm leading-relaxed space-y-1">
          {data.aiNarrative.split('\n').map((line: string, i: number) => {
            const t = line.trim();
            if (!t) return <div key={i} className="h-2" />;
            if (SECTION_TITLES.test(t)) return <h4 key={i} className="text-base font-extrabold text-[#007AFF] mt-3 mb-1">{t}</h4>;
            return <p key={i} className="text-[#3A3A3C] mb-1">{t}</p>;
          })}
        </div>
        <p className="text-xs text-[#8E8E93]/70 mt-4">本结果仅基于用户上传数据进行非医疗分析</p>
      </div>

      <Link href={`/reports/${reportId}/chat`}
        className="block w-full text-center mt-6 py-3 bg-[#007AFF] text-white rounded-full font-bold hover:bg-[#0077EE] transition-colors shadow-lg shadow-[#007AFF]/20">
        🤖 深入对话
      </Link>
    </div>
  );
}
