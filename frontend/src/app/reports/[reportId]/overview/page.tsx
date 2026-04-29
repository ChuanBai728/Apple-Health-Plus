'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import { useParams } from 'next/navigation';
import { getOverview, getInsight, sendChatMessage } from '@/lib/api';
import { OverviewResponse, METRIC_LABELS, METRIC_UNITS, isHiddenMetric, getTrendColor, getCategory, catLabel } from '@/lib/types';
import Link from 'next/link';

type TimeRange = 'all' | 'year' | 'month' | 'week';

/* ── Side Panel ──────────────────────────────────── */
const SEC_RE = /^(整体评估|关键发现|风险提示|可执行建议|核心指标|健康总结)/;

/* ── Number highlighter ──────────────────────────── */
function hlNums(text: string) {
  const re = /(?:\d+\.?\d*\s*(?:bpm|ms|步|kcal|kg|h|min|%|km)|[+-]\d+\.?\d*%|\d+\.?\d*\s*[上下]降)/g;
  const parts = text.split(re); const matches = text.match(re)||[];
  return parts.map((p,i)=>i===0?<span key={i}>{p}</span>:<span key={i}>{matches[i-1]?<mark className="bg-blue-50 text-blue-700 px-1.5 py-0.5 rounded font-medium text-[11px]">{matches[i-1]}</mark>:null}{p}</span>);
}

/* ── Left Report Panel ───────────────────────────── */
function ReportPanel({ insight, insightType, setInsightType }: any) {
  return (
    <div className="h-full bg-white rounded-3xl border border-transparent shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-6 space-y-6 overflow-y-auto">
      <div className="flex items-center justify-between">
        <span className="text-sm font-bold text-slate-900">健康报告</span>
        <div className="flex gap-1 bg-slate-100 rounded-lg p-0.5">
          <button onClick={()=>setInsightType('weekly')} className={`px-2.5 py-0.5 text-xs rounded-md font-medium transition ${insightType==='weekly'?'bg-white text-slate-900 shadow-sm':'text-slate-500'}`}>周</button>
          <button onClick={()=>setInsightType('monthly')} className={`px-2.5 py-0.5 text-xs rounded-md font-medium transition ${insightType==='monthly'?'bg-white text-slate-900 shadow-sm':'text-slate-500'}`}>月</button>
        </div>
      </div>
      {!insight && <div className="text-center py-8 text-xs text-slate-400">加载中...</div>}
      {insight && <>
        <div className="text-xs text-slate-400">{insight.startDate} ~ {insight.endDate}</div>
        <div className="space-y-1">{insight.highlights?.slice(0,8).map((h:any)=>(
          <div key={h.metricKey} className="flex items-center gap-2 bg-slate-50 rounded-xl px-3 py-2 text-xs">
            <span className="flex-1 text-slate-700 font-medium">{METRIC_LABELS[h.metricKey]||h.metricKey}</span>
            <span className="font-semibold text-slate-900">{Number.isInteger(h.weeklyAvg)?h.weeklyAvg:h.weeklyAvg.toFixed(1)}{METRIC_UNITS[h.metricKey]||''}</span>
            <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${h.changePct>0?'bg-emerald-100/50 text-emerald-700':'bg-rose-100/50 text-rose-700'}`}>{h.trend}{Math.abs(h.changePct).toFixed(0)}%</span>
          </div>
        ))}</div>
        <div className="border-t border-slate-100" />
        <div className="text-base text-slate-600 leading-relaxed space-y-5">
          {insight.aiNarrative.split('\n').map((line:string,i:number)=>{
            const t=line.trim();
            if(!t) return <div key={i} />;
            if(SEC_RE.test(t)) return <div key={i} className="border-t border-slate-100 pt-5 -mt-1 first:border-t-0 first:pt-4">
              <div className="text-lg font-semibold text-slate-800 mb-3">{t}</div>
            </div>;
            return <p key={i} className="leading-loose">{hlNums(t)}</p>;
          })}
        </div>
      </>}
    </div>
  );
}

/* ── Right Chat Panel ────────────────────────────── */
function ChatPanel({ chatMsgs, chatInput, setChatInput, chatLoading, sendMsg }: any) {
  return (
    <div className="h-full bg-white rounded-3xl border border-transparent shadow-[0_8px_30px_rgb(0,0,0,0.04)] flex flex-col overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 text-sm font-bold text-slate-900 shrink-0">AI 对话</div>
      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-3">
        {chatMsgs.length===0&&(
          <div className="flex flex-col items-center justify-center h-full text-center space-y-5">
            <div className="w-16 h-16 rounded-full bg-slate-100 flex items-center justify-center text-2xl">🤖</div>
            <div>
              <p className="text-sm font-semibold text-slate-700">健康智能助手</p>
              <p className="text-xs text-slate-400 mt-1">基于你的数据，随时提问</p>
            </div>
            <div className="flex flex-wrap gap-2 justify-center">
              {['整体健康状态','睡眠质量如何','恢复状态怎么样','心率正常吗'].map(q=>(
                <button key={q} onClick={()=>sendMsg(q)}
                  className="px-4 py-2 bg-slate-50 hover:bg-slate-100 rounded-xl text-base text-slate-600 transition-colors">{q}</button>
              ))}
            </div>
          </div>
        )}
        {chatMsgs.map((m:any,i:number)=>(
          <div key={i} className={`flex ${m.role==='user'?'justify-end':'justify-start'}`}>
            <div className={`max-w-[85%] rounded-2xl px-4 py-2.5 text-base leading-relaxed ${
              m.role==='user'?'bg-[#007AFF] text-white rounded-br-sm':'bg-slate-100 text-slate-700 rounded-bl-sm'
            }`}>{m.content}</div>
          </div>
        ))}
        {chatLoading&&<div className="flex items-center gap-2 text-xs text-slate-400 px-2">
          <div className="animate-spin h-3 w-3 border-2 border-slate-300 border-t-slate-500 rounded-full"/>思考中...</div>}
      </div>
      <div className="px-3 pb-3 pt-2 border-t border-slate-100 shrink-0 backdrop-blur-md bg-white/80 rounded-b-3xl">
        <div className="flex gap-2 items-center bg-slate-100 rounded-full px-4 py-1.5">
          <input value={chatInput} onChange={e=>setChatInput((e.target as HTMLInputElement).value)}
            onKeyDown={e=>e.key==='Enter'&&sendMsg(chatInput)}
            placeholder="输入问题..." disabled={chatLoading}
            className="flex-1 bg-transparent text-base text-slate-700 placeholder-slate-400 focus:outline-none"/>
          <button onClick={()=>sendMsg(chatInput)} disabled={chatLoading||!chatInput.trim()}
            className="w-8 h-8 flex items-center justify-center bg-[#007AFF] text-white rounded-full disabled:opacity-40 hover:bg-[#0077EE] transition-colors shrink-0">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="5 12 12 5 19 12"/></svg>
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── Sparkline ───────────────────────────────────── */
function SvgSparkline({ points, w = 80, h = 28, color = '#8E8E93' }: { points: (number|null)[]; w?: number; h?: number; color?: string }) {
  const vals = points.filter((v): v is number => v != null);
  if (vals.length < 2) return <div className="text-gray-300 text-xs">—</div>;
  const min = Math.min(...vals), max = Math.max(...vals);
  const range = max - min || 1;
  const p = 2, pw = w - p*2, ph = h - p*2;
  const pts = vals.map((v,i) => `${p+(i/(vals.length-1))*pw},${p+ph-((v-min)/range)*ph}`).join(' ');
  return <svg width={w} height={h} className="opacity-70"><polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>;
}

/* ── Hero ────────────────────────────────────────── */
function HeroCard({ data, range, onRange, id }: { data: OverviewResponse; range: TimeRange; onRange: (r:TimeRange)=>void; id: string }) {
  const steps = data.cards.find(c=>c.metricKey==='step_count');
  const rhr   = data.cards.find(c=>c.metricKey==='resting_heart_rate');
  const hrv   = data.cards.find(c=>c.metricKey==='heart_rate_variability_sdnn');
  const sleep = data.cards.find(c=>c.metricKey==='sleep_duration');
  const workout = data.cards.find(c=>c.metricKey==='workout');
  const energy  = data.cards.find(c=>c.metricKey==='active_energy_burned');
  const n = data.cards.filter(c=>c.anomaly).length;
  const state = n>3?'值得关注':n>0?'基本稳定':'状态良好';
  const stateC = n>3?'text-[#FF9500] bg-[#FF9500]/8':n>0?'text-[#007AFF] bg-[#007AFF]/8':'text-[#34C759] bg-[#34C759]/8';

  // Activity ring percentages (idealized for display)
  const stepGoal = 10000; const energyGoal = 800; const exerciseGoal = 30;
  const movePct  = Math.min((energy?.latest??0) / energyGoal, 1);
  const exPct    = Math.min((workout?.latest??0) / exerciseGoal, 1);
  const standPct = Math.min(((rhr?.latest??0) > 0 ? 0.83 : 0), 1); // simplified

  return (
    <div className="col-span-2 row-span-2 bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF] rounded-3xl p-6 border border-black/5 flex flex-col justify-between shadow-[0_8px_30px_rgb(0,0,0,0.04)]">
      {/* Top: status + headline */}
      <div>
        <div className="flex items-center justify-between">
          <p className="text-xs font-bold text-[#8E8E93] uppercase tracking-[0.2em]">健康概览</p>
          <span className={`text-xs font-bold px-3 py-1 rounded-full ${stateC}`}>{state}</span>
        </div>
        <p className="text-sm text-slate-700 leading-relaxed mt-2">{data.headline}</p>
      </div>

      {/* Middle: step count + activity rings */}
      <div className="flex items-center justify-between my-3">
        {/* Left: step count */}
        <div className="text-center min-w-[100px]">
          <div className="text-xs font-semibold text-[#8E8E93] uppercase tracking-wider">今日步数</div>
          <div className="text-4xl font-extrabold text-[#1C1C1E] tabular-nums leading-none mt-1">
            {steps?.latest != null ? Math.round(steps.latest).toLocaleString() : '—'}
          </div>
          <div className="text-xs text-[#8E8E93] mt-1">目标 {stepGoal.toLocaleString()}</div>
          <div className="mt-2 mx-auto max-w-[120px] bg-black/[0.06] rounded-full h-1.5 overflow-hidden">
            <div className="bg-gradient-to-r from-[#FF9500] to-[#FF3B30] h-full rounded-full"
              style={{width: `${Math.min((steps?.latest??0)/stepGoal*100, 100)}%`}} />
          </div>
        </div>

        {/* Right: 3-color activity rings */}
        <div className="flex items-center gap-5">
          <div className="relative w-[76px] h-[76px] shrink-0">
            <svg viewBox="0 0 88 88" className="w-full h-full -rotate-90">
              {/* Move (red) - outer */}
              <circle cx="44" cy="44" r="34" fill="none" stroke="rgba(0,0,0,0.06)" strokeWidth="9"/>
              <circle cx="44" cy="44" r="34" fill="none" stroke="#FF3B30" strokeWidth="9"
                strokeDasharray={`${movePct * 2.14 * 100} ${214 - movePct * 2.14 * 100}`} strokeLinecap="round"/>
              {/* Exercise (green) - middle */}
              <circle cx="44" cy="44" r="23" fill="none" stroke="rgba(0,0,0,0.06)" strokeWidth="9"/>
              <circle cx="44" cy="44" r="23" fill="none" stroke="#34C759" strokeWidth="9"
                strokeDasharray={`${exPct * 1.45 * 100} ${145 - exPct * 1.45 * 100}`} strokeLinecap="round"/>
              {/* Stand (blue) - inner */}
              <circle cx="44" cy="44" r="12" fill="none" stroke="rgba(0,0,0,0.06)" strokeWidth="9"/>
              <circle cx="44" cy="44" r="12" fill="none" stroke="#007AFF" strokeWidth="9"
                strokeDasharray={`${standPct * 0.75 * 100} ${75 - standPct * 0.75 * 100}`} strokeLinecap="round"/>
            </svg>
          </div>
          <div className="text-xs text-[#8E8E93] leading-relaxed">
            <div><span className="inline-block w-2 h-2 rounded-full bg-[#FF3B30] mr-1.5"/>活动 {energy?.latest??'—'} kcal</div>
            <div><span className="inline-block w-2 h-2 rounded-full bg-[#34C759] mr-1.5"/>锻炼 {workout?.latest??'—'} min</div>
            <div><span className="inline-block w-2 h-2 rounded-full bg-[#007AFF] mr-1.5"/>站立 10 h</div>
          </div>
        </div>
      </div>

      {/* Bottom: 4-column vitals */}
      <div className="grid grid-cols-4 gap-2 mb-3">
        <div className="bg-black/[0.02] rounded-xl p-2.5 text-center">
          <div className="text-sm text-[#8E8E93]">心率</div>
          <div className="text-base font-bold text-[#1C1C1E] tabular-nums">{rhr?.latest??'—'}<span className="text-sm font-normal text-[#8E8E93]"> bpm</span></div>
        </div>
        <div className="bg-black/[0.02] rounded-xl p-2.5 text-center">
          <div className="text-sm text-[#8E8E93]">HRV</div>
          <div className="text-base font-bold text-[#1C1C1E] tabular-nums">{hrv?.latest??'—'}<span className="text-sm font-normal text-[#8E8E93]"> ms</span></div>
        </div>
        <div className="bg-black/[0.02] rounded-xl p-2.5 text-center">
          <div className="text-sm text-[#8E8E93]">运动</div>
          <div className="text-base font-bold text-[#1C1C1E] tabular-nums">{workout?.latest??'—'}<span className="text-sm font-normal text-[#8E8E93]"> min</span></div>
        </div>
        <div className="bg-black/[0.02] rounded-xl p-2.5 text-center">
          <div className="text-sm text-[#8E8E93]">睡眠</div>
          <div className="text-base font-bold text-[#1C1C1E] tabular-nums">{sleep?.latest?.toFixed(1)??'—'}<span className="text-sm font-normal text-[#8E8E93]"> h</span></div>
        </div>
      </div>

      {/* Bottom bar: time range + AI report link */}
      <div className="flex items-center gap-2">
        <div className="flex gap-1 bg-black/[0.04] rounded-full p-0.5">
          {(['week','month','year','all'] as TimeRange[]).map(r=><button key={r} onClick={()=>onRange(r)} className={`px-3.5 py-1.5 text-xs rounded-full font-semibold transition ${range===r?'bg-white text-[#1C1C1E] shadow-sm':'text-[#8E8E93] hover:text-slate-700 hover:bg-black/[0.02]'}`}>{ {week:'周',month:'月',year:'年',all:'全部'}[r]}</button>)}
        </div>
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
      className="col-span-1 row-span-1 bg-white rounded-3xl border border-transparent p-5 hover:-translate-y-1 hover:shadow-lg hover:border-slate-200 transition-all duration-300 group flex flex-col justify-between min-h-[150px]">
      <div>
        <div className="flex items-start justify-between">
          <p className="text-sm font-semibold text-slate-500 uppercase tracking-[0.1em] leading-tight">{l}</p>
          {card.anomaly && <span className="w-[6px] h-[6px] bg-[#FF9500] rounded-full shrink-0 mt-0.5"/>}
        </div>
        <div className="flex items-baseline gap-1 mt-1">
          <span className="text-5xl font-bold text-slate-900 tabular-nums leading-none">
            {v!=null?(Number.isInteger(v)?v:v.toFixed(1)):'—'}
          </span>
          <span className="text-sm text-slate-500 font-medium">{u}</span>
        </div>
        {card.trend30d!=null && (
          <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium mt-1 ${
            tr.includes('emerald')?'bg-emerald-100/50 text-emerald-700':
            tr.includes('rose')?'bg-rose-100/50 text-rose-700':
            'bg-slate-100 text-slate-500'}`}>
            {card.trend30d>0?'↑':card.trend30d<0?'↓':'→'} {Math.abs(card.trend30d).toFixed(1)}
          </span>
        )}
      </div>
      <div className="h-8 mt-1"><SvgSparkline points={pts} color="#cbd5e1"/></div>
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
      className="col-span-2 row-span-1 bg-white rounded-3xl border border-transparent p-5 hover:-translate-y-1 hover:shadow-lg hover:border-slate-200 transition-all duration-300 flex items-center gap-6 min-h-[120px]">
      <div className="shrink-0 min-w-[140px]">
        <p className="text-sm font-semibold text-slate-500 uppercase tracking-[0.1em]">{l}</p>
        <div className="flex items-baseline gap-1 mt-1">
          <span className="text-5xl font-bold text-slate-900 tabular-nums leading-none">
            {v!=null?(Number.isInteger(v)?v:v.toFixed(1)):'—'}
          </span>
          <span className="text-sm text-slate-500 font-medium">{u}</span>
        </div>
        {card.trend30d!=null&&<span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium mt-1 ${
          tr.includes('emerald')?'bg-emerald-100/50 text-emerald-700':
          tr.includes('rose')?'bg-rose-100/50 text-rose-700':
          'bg-slate-100 text-slate-500'}`}>{card.trend30d>0?'↑':'↓'} {Math.abs(card.trend30d).toFixed(1)}</span>}
      </div>
      <div className="flex-1 h-12"><SvgSparkline points={pts} w={300} h={48} color={sc}/></div>
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
  const [insight, setInsight] = useState<any>(null);
  const [insightType, setInsightType] = useState<'weekly'|'monthly'>('weekly');
  const [chatMsgs, setChatMsgs] = useState<{role:string;content:string}[]>([]);
  const [chatInput, setChatInput] = useState('');
  const [chatLoading, setChatLoading] = useState(false);

  useEffect(() => { getOverview(reportId).then(setData).catch(e=>setError(e.message)); }, [reportId]);
  const insightCK = `hp-insight-${reportId}-${insightType}`;
  useEffect(() => {
    const c = localStorage.getItem(insightCK);
    if (c) { setInsight(JSON.parse(c)); return; }
    getInsight(reportId, insightType).then(d => {
      setInsight(d);
      try { localStorage.setItem(insightCK, JSON.stringify(d)); } catch {}
    }).catch(()=>{});
  }, [reportId, insightType, insightCK]);

  const sendMsg = useCallback(async (q: string) => {
    if (!q.trim() || chatLoading) return;
    setChatMsgs(p=>[...p,{role:'user',content:q}]); setChatInput(''); setChatLoading(true);
    try {
      const h=reportId.replace(/-/g,'').substring(0,12).padEnd(32,'0');
      const sid=h.replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/,'$1-$2-$3-$4-$5');
      const r=await sendChatMessage(sid,{question:q,uploadId:reportId});
      setChatMsgs(p=>[...p,{role:'assistant',content:r.conclusion}]);
    } catch {} finally { setChatLoading(false); }
  }, [reportId, chatLoading]);

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
  const forceCollapsed = new Set(['environmental_audio_exposure','apple_walking_steadiness','walking_asymmetry_percentage','headphone_audio_exposure','apple_stand_time','walking_running_distance','distance_walking_running']);
  const primary = ['resting_heart_rate','heart_rate_variability_sdnn','sleep_duration','workout'];
  const trends  = ['body_mass','step_count'];
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
    <div className="w-full max-w-[1920px] mx-auto px-6 flex gap-5 py-4 h-[calc(100vh-48px)]">
      <div className="w-[320px] 2xl:w-[400px] shrink-0 h-full"><ReportPanel insight={insight} insightType={insightType} setInsightType={setInsightType} /></div>
      <div className="flex-1 min-w-0 space-y-5 overflow-y-auto h-full">
        {/* ── Top bar ── */}
        <div className="flex items-center gap-3">
          <Link href="/" className="text-xs font-medium text-[#007AFF] hover:underline transition-colors">← 首页</Link>
          <div className="flex-1"/>
          <Link href={`/reports/${reportId}/heatmap`} className="px-3.5 py-2 bg-white border border-black/5 rounded-full text-xs font-medium hover:bg-black/[0.02] transition-colors">🗓 热力图</Link>
        </div>

      {/* ── Anomaly alert ── */}
      {anomalyCards.length>0 && (
        <div className="bg-[#FFFBF5] border border-[#FF9500]/15 rounded-3xl px-5 py-3 flex items-center gap-3">
          <span className="text-sm font-bold text-[#FF9500] shrink-0">⚠ {anomalyCards.length} 项异常</span>
          <div className="flex gap-1.5 flex-wrap">
            {anomalyCards.map(c=><Link key={c.metricKey} href={`/reports/${reportId}/metrics/${c.metricKey}`} className="px-2.5 py-0.5 bg-white border border-[#FF9500]/20 rounded-full text-xs text-[#FF9500] hover:bg-[#FF9500]/5 transition-colors">{METRIC_LABELS[c.metricKey]||c.metricKey}</Link>)}
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
      <div className="bg-white rounded-3xl border border-black/5 p-5">
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
      <div className="w-[320px] 2xl:w-[380px] shrink-0 h-full"><ChatPanel chatMsgs={chatMsgs} chatInput={chatInput} setChatInput={setChatInput} chatLoading={chatLoading} sendMsg={sendMsg} /></div>
    </div>
  );
}
