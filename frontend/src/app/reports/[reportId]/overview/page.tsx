'use client';

import { useEffect, useState, useMemo } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { getOverview } from '@/lib/api';
import { OverviewResponse, METRIC_LABELS, METRIC_UNITS, isHiddenMetric, getTrendColor, getCategory, catLabel } from '@/lib/types';
import Link from 'next/link';

type TimeRange = 'all' | 'year' | 'month' | 'week';

/* ── Sparkline ───────────────────────────────────── */
function SvgSparkline({ points, w = 80, h = 28, color = '#8E8E93' }: { points: (number|null)[]; w?: number; h?: number; color?: string }) {
  const vals = points.filter((v): v is number => v != null);
  if (vals.length < 2) return <div className="text-gray-300 text-[10px]">—</div>;
  const min = Math.min(...vals), max = Math.max(...vals);
  const range = max - min || 1;
  const p = 2, pw = w - p*2, ph = h - p*2;
  const pts = vals.map((v,i) => `${p+(i/(vals.length-1))*pw},${p+ph-((v-min)/range)*ph}`).join(' ');
  return <svg width={w} height={h} className="opacity-70"><polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>;
}

/* ── Hero ────────────────────────────────────────── */
function HeroCard({ data, range, onRange, id }: { data: OverviewResponse; range: TimeRange; onRange: (r:TimeRange)=>void; id: string }) {
  const steps = data.cards.find(c=>c.metricKey==='step_count');
  const rhr  = data.cards.find(c=>c.metricKey==='resting_heart_rate');
  const hrv  = data.cards.find(c=>c.metricKey==='heart_rate_variability_sdnn');
  const n = data.cards.filter(c=>c.anomaly).length;
  const state = n>3?'值得关注':n>0?'基本稳定':'状态良好';
  const stateC = n>3?'text-[#FF9500] bg-[#FF9500]/8':n>0?'text-[#007AFF] bg-[#007AFF]/8':'text-[#34C759] bg-[#34C759]/8';

  return (
    <div className="col-span-2 row-span-2 bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF] rounded-3xl p-6 border border-black/5 flex flex-col justify-between shadow-[0_2px_12px_rgba(0,0,0,0.03)]">
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <p className="text-xs font-bold text-[#8E8E93] uppercase tracking-[0.2em]">健康概览</p>
          <span className={`text-xs font-bold px-3 py-1 rounded-full ${stateC}`}>{state}</span>
        </div>
        <p className="text-sm text-[#3A3A3C] leading-relaxed">{data.headline}</p>
      </div>

      {/* Activity ring */}
      <div className="flex items-center gap-6">
        <div className="relative w-[88px] h-[88px] shrink-0">
          <svg viewBox="0 0 88 88" className="w-full h-full -rotate-90">
            <circle cx="44" cy="44" r="36" fill="none" stroke="rgba(0,0,0,0.06)" strokeWidth="6"/>
            <circle cx="44" cy="44" r="36" fill="none" stroke="#FF9500" strokeWidth="6"
              strokeDasharray="226" strokeDashoffset="78" strokeLinecap="round"/>
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-xl font-extrabold tabular-nums leading-none text-[#1C1C1E]">{steps?.latest?Math.round(steps.latest/1000)+'k':'—'}</span>
            <span className="text-xs text-[#8E8E93]">步</span>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-x-6 gap-y-2">
          <span className="text-sm text-[#8E8E93]">静息心率</span><span className="text-sm font-semibold tabular-nums text-right text-[#1C1C1E]">{rhr?.latest??'—'}<span className="text-[#8E8E93]/70 ml-1 text-xs">bpm</span></span>
          <span className="text-sm text-[#8E8E93]">HRV</span><span className="text-sm font-semibold tabular-nums text-right text-[#1C1C1E]">{hrv?.latest??'—'}<span className="text-[#8E8E93]/70 ml-1 text-xs">ms</span></span>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <div className="flex gap-1 bg-black/[0.04] rounded-full p-0.5">
          {(['week','month','year','all'] as TimeRange[]).map(r=><button key={r} onClick={()=>onRange(r)} className={`px-3.5 py-1.5 text-xs rounded-full font-semibold transition ${range===r?'bg-white text-[#1C1C1E] shadow-sm':'text-[#8E8E93] hover:text-[#3A3A3C] hover:bg-black/[0.02]'}`}>{ {week:'周',month:'月',year:'年',all:'全部'}[r]}</button>)}
        </div>
        <Link href={`/reports/${id}/report`}
          className="ml-auto px-4 py-1.5 bg-black/[0.02] hover:bg-black/[0.04] text-[#007AFF] rounded-full text-xs font-semibold transition flex items-center gap-1.5 border border-black/5">
          📋 AI 健康报告
        </Link>
      </div>
    </div>
  );
}

/* ── Snapshot (1×1) ──────────────────────────────── */
function SnapshotCard({ card, id }: { card: any; id: string }) {
  const l = METRIC_LABELS[card.metricKey]||card.metricKey;
  const u = METRIC_UNITS[card.metricKey]||'';
  const tr = getTrendColor(card.metricKey, card.trend30d);
  const v = card.latest;
  const pts = card.recentPoints?.map((p:any)=>p.value)??[];

  return (
    <Link href={`/reports/${id}/metrics/${card.metricKey}`}
      className="col-span-1 row-span-1 bg-white/70 backdrop-blur-xl rounded-2xl border border-black/5 p-4 hover:border-black/10 hover:shadow-md transition-all group flex flex-col justify-between min-h-[140px]">
      <div>
        <div className="flex items-start justify-between">
          <p className="text-[10px] font-semibold text-[#8E8E93] uppercase tracking-[0.1em] leading-tight">{l}</p>
          {card.anomaly && <span className="w-[6px] h-[6px] bg-[#FF9500] rounded-full shrink-0 mt-0.5"/>}
        </div>
        <div className="flex items-baseline gap-1 mt-1">
          <span className="text-[28px] font-extrabold text-[#1C1C1E] tabular-nums leading-none">
            {v!=null?(Number.isInteger(v)?v:v.toFixed(1)):'—'}
          </span>
          <span className="text-[10px] text-[#8E8E93] font-medium">{u}</span>
        </div>
        {card.trend30d!=null && (
          <span className={`inline-block text-[10px] font-semibold mt-0.5 px-1.5 py-0.5 rounded-md ${tr.includes('emerald')?'bg-[#34C759]/10 text-[#34C759]':tr.includes('rose')?'bg-[#FF3B30]/10 text-[#FF3B30]':'bg-black/5 text-[#8E8E93]'}`}>
            {card.trend30d>0?'↑':card.trend30d<0?'↓':'→'} {Math.abs(card.trend30d).toFixed(1)}
          </span>
        )}
      </div>
      <div className="h-7 mt-1"><SvgSparkline points={pts} color="#cbd5e1"/></div>
    </Link>
  );
}

/* ── Trend (2×1) ─────────────────────────────────── */
function TrendCard({ card, id }: { card: any; id: string }) {
  const l = METRIC_LABELS[card.metricKey]||card.metricKey;
  const u = METRIC_UNITS[card.metricKey]||'';
  const tr = getTrendColor(card.metricKey, card.trend30d);
  const v = card.latest;
  const pts = card.recentPoints?.map((p:any)=>p.value)??[];
  const sc = tr.includes('emerald')?'#34C759':tr.includes('rose')?'#FF3B30':'#007AFF';

  return (
    <Link href={`/reports/${id}/metrics/${card.metricKey}`}
      className="col-span-2 row-span-1 bg-white/70 backdrop-blur-xl rounded-2xl border border-black/5 p-4 hover:border-black/10 hover:shadow-md transition-all flex items-center gap-6 min-h-[100px]">
      <div className="shrink-0 min-w-[130px]">
        <p className="text-[10px] font-semibold text-[#8E8E93] uppercase tracking-[0.1em]">{l}</p>
        <div className="flex items-baseline gap-1 mt-1">
          <span className="text-[28px] font-extrabold text-[#1C1C1E] tabular-nums leading-none">
            {v!=null?(Number.isInteger(v)?v:v.toFixed(1)):'—'}
          </span>
          <span className="text-[10px] text-[#8E8E93] font-medium">{u}</span>
        </div>
        {card.trend30d!=null&&<span className={`inline-block text-[10px] font-semibold mt-0.5 px-1.5 py-0.5 rounded-md ${tr.includes('emerald')?'bg-[#34C759]/10 text-[#34C759]':tr.includes('rose')?'bg-[#FF3B30]/10 text-[#FF3B30]':'bg-black/5 text-[#8E8E93]'}`}>{card.trend30d>0?'↑':'↓'} {Math.abs(card.trend30d).toFixed(1)}</span>}
      </div>
      <div className="flex-1 h-12"><SvgSparkline points={pts} w={280} h={48} color={sc}/></div>
    </Link>
  );
}

/* ── Mini Metric (compact inline) ─────────────────── */
function MiniMetric({ card, id }: { card: any; id: string }) {
  const l = METRIC_LABELS[card.metricKey]||card.metricKey;
  const u = METRIC_UNITS[card.metricKey]||'';
  const v = card.latest;
  return (
    <Link href={`/reports/${id}/metrics/${card.metricKey}`}
      className="flex items-center justify-between px-3 py-2 rounded-xl hover:bg-black/[0.02] transition-colors group">
      <span className="text-sm text-[#8E8E93] truncate max-w-[140px]">{l}</span>
      <span className="text-base font-semibold text-[#1C1C1E] tabular-nums shrink-0">
        {v!=null?(Number.isInteger(v)?v:v.toFixed(1)):'—'}
        <span className="text-xs text-[#8E8E93]/70 ml-0.5 font-normal">{u}</span>
      </span>
    </Link>
  );
}

/* ── Main ────────────────────────────────────────── */
export default function OverviewPage() {
  const { reportId } = useParams<{ reportId: string }>();
  const [data, setData] = useState<OverviewResponse|null>(null);
  const [error, setError] = useState('');
  const [range, setRange] = useState<TimeRange>('all');
  const [showAll, setShowAll] = useState(false);

  useEffect(() => { getOverview(reportId).then(setData).catch(e=>setError(e.message)); }, [reportId]);

  const visible = useMemo(() => data?.cards.filter(c=>!isHiddenMetric(c.metricKey))??[], [data]);

  const filtered = useMemo(() => {
    const since = range==='week'?Date.now()-7*864e5:range==='month'?Date.now()-30*864e5:range==='year'?Date.now()-365*864e5:null;
    return visible.map(card => {
      if(!since) return card;
      const pts = card.recentPoints?.filter((p:any)=>new Date(p.date)>=new Date(since))??[];
      if(!pts.length) return null;
      const vals = pts.map((p:any)=>p.value).filter((v:any):v is number=>v!=null);
      return {...card, latest: vals[vals.length-1]??card.latest, recentPoints: pts};
    }).filter(Boolean) as typeof visible;
  }, [visible, range]);

  // Card layout: primary (grid snapshots 1×1), trends (grid 2×1), others (collapsed section)
  const alwaysVisible = new Set(['heart_rate','workout','resting_heart_rate','heart_rate_variability_sdnn','oxygen_saturation','respiratory_rate','body_mass','step_count','active_energy_burned','body_fat_percentage']);
  // Metrics forced into collapsed section (hidden from main grid)
  const forceCollapsed = new Set(['environmental_audio_exposure','apple_walking_steadiness','walking_asymmetry_percentage','headphone_audio_exposure']);
  const primary = ['resting_heart_rate','heart_rate_variability_sdnn','oxygen_saturation','respiratory_rate'];
  const trends  = ['body_mass'];
  const pCards  = filtered.filter(c=>primary.includes(c.metricKey));
  const tCards  = filtered.filter(c=>trends.includes(c.metricKey));
  const others  = filtered.filter(c=>!alwaysVisible.has(c.metricKey) || (!primary.includes(c.metricKey)&&!trends.includes(c.metricKey)&&!alwaysVisible.has(c.metricKey)));
  // Exclude primary/trends from others, and exclude always_visible metrics that are already in primary/trends
  const othersFiltered = filtered.filter(c=>!primary.includes(c.metricKey)&&!trends.includes(c.metricKey));
  const othersByCat = useMemo(() => {
    const m: Record<string,any[]> = {};
    for (const c of othersFiltered) { const cat = getCategory(c.metricKey); if(!m[cat]) m[cat]=[]; m[cat].push(c); }
    return m;
  }, [othersFiltered]);
  const anomalyCards = filtered.filter(c=>c.anomaly);

  if(error) return <div className="text-center py-20"><p className="text-[#FF3B30]">{error}</p><Link href="/" className="text-[#007AFF] text-sm">← 返回</Link></div>;
  if(!data) return <div className="flex justify-center py-20"><div className="animate-spin h-8 w-8 border-4 border-[#007AFF] border-t-transparent rounded-full"/></div>;

  return (
    <div className="space-y-5 max-w-[960px] mx-auto pb-10">
      {/* ── Top bar ── */}
      <div className="flex items-center gap-3">
        <Link href="/" className="text-xs font-medium text-[#007AFF] hover:underline transition-colors">← 首页</Link>
        <div className="flex-1"/>
        <Link href={`/reports/${reportId}/chat`}
          className="px-5 py-2.5 bg-[#007AFF] text-white rounded-full text-sm font-bold hover:bg-[#0077EE] transition-all shadow-md shadow-[#007AFF]/20 flex items-center gap-1.5">
          🤖 AI 对话
        </Link>
        <Link href={`/reports/${reportId}/heatmap`} className="px-3.5 py-2 bg-white/70 backdrop-blur-xl border border-black/5 rounded-full text-xs font-medium hover:bg-black/[0.02] transition-colors">🗓 热力图</Link>
      </div>

      {/* ── Anomaly alert ── */}
      {anomalyCards.length>0 && (
        <div className="bg-[#FFFBF5] border border-[#FF9500]/15 rounded-2xl px-5 py-3 flex items-center gap-3">
          <span className="text-[11px] font-bold text-[#FF9500] shrink-0">⚠ {anomalyCards.length} 项异常</span>
          <div className="flex gap-1.5 flex-wrap">
            {anomalyCards.map(c=><Link key={c.metricKey} href={`/reports/${reportId}/metrics/${c.metricKey}`} className="px-2.5 py-0.5 bg-white border border-[#FF9500]/20 rounded-full text-[10px] text-[#FF9500] hover:bg-[#FF9500]/5 transition-colors">{METRIC_LABELS[c.metricKey]||c.metricKey}</Link>)}
          </div>
        </div>
      )}

      {/* ── CSS Grid Bento ── */}
      <div className="grid grid-cols-4 gap-4">

        {/* Row 1-2: Hero + 2 snapshots */}
        <HeroCard data={data} range={range} onRange={setRange} id={reportId}/>
        {pCards.slice(0,2).map(c=><SnapshotCard key={c.metricKey} card={c} id={reportId}/>)}

        {/* Row 2: 2 more snapshots */}
        {pCards.slice(2,4).map(c=><SnapshotCard key={c.metricKey} card={c} id={reportId}/>)}

        {/* Row 3: 2 trend cards */}
        {tCards.map(c=><TrendCard key={c.metricKey} card={c} id={reportId}/>)}

        {/* Row 4-5: Top 8 other metrics as snapshots if showAll=false */}
        {!showAll && othersFiltered.filter(c=>!forceCollapsed.has(c.metricKey)).slice(0,8).map(c=><SnapshotCard key={c.metricKey} card={c} id={reportId}/>)}
      </div>

      {/* ── Collapsible: 更多指标 ── */}
      <div className="bg-white/70 backdrop-blur-xl rounded-2xl border border-black/5 p-5">
        <button onClick={()=>setShowAll(!showAll)}
          className="flex items-center gap-2 text-sm font-semibold text-[#8E8E93] hover:text-[#1C1C1E] transition-colors w-full">
          <span className={`transform transition-transform ${showAll?'rotate-90':''}`}>▸</span>
          {showAll?`收起分类指标 (${othersFiltered.length})`:`展开全部指标 (${othersFiltered.length} 项，按分类排列)`}
        </button>

        {showAll && (
          <div className="mt-4 space-y-5">
            {Object.entries(othersByCat).map(([cat, cards]) => (
              <div key={cat}>
                <h4 className="text-xs font-bold text-[#8E8E93] uppercase tracking-[0.15em] mb-2">{catLabel(cat)}</h4>
                <div className="grid grid-cols-2 gap-x-4 gap-y-1">
                  {cards.map((c:any)=><MiniMetric key={c.metricKey} card={c} id={reportId}/>)}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
