import { OverviewCard, METRIC_LABELS, METRIC_UNITS, getTrendColor } from '@/lib/types';
import Link from 'next/link';
import { Sparkline } from './Sparkline';

const ACCENT_COLORS: Record<string, string> = {
  activity: '#FF3B30', heart: '#FF3B30', body: '#34C759', sleep_env: '#5856D6',
};

export function MetricCard({ card, reportId, category }: { card: OverviewCard; reportId: string; category: string }) {
  const label = METRIC_LABELS[card.metricKey] || card.label || card.metricKey;
  const unit = card.unit || METRIC_UNITS[card.metricKey] || '';
  const trend = card.trend30d;
  const trendColor = trend != null ? getTrendColor(card.metricKey, trend) : '';
  const color = ACCENT_COLORS[category] || '#6b7280';
  const sparkPoints = (card.recentPoints || []).map(p => p.value);

  return (
    <Link
      href={`/reports/${reportId}/metrics/${card.metricKey}`}
      className={`block bg-white/70 backdrop-blur-xl rounded-2xl border overflow-hidden hover:shadow-md hover:-translate-y-0.5 transition-all ${
        card.anomaly ? 'border-[#FF9500]/30 ring-1 ring-[#FF9500]/20' : 'border-black/5'
      }`}
    >
      <div className="h-1" style={{ backgroundColor: color }} />
      <div className="p-3">
        <div className="flex items-center justify-between">
          <span className="text-xs font-medium text-[#8E8E93]">{label}</span>
          {card.anomaly && <span className="text-[10px] bg-[#FF9500]/10 text-[#FF9500] px-1.5 py-0.5 rounded-full font-medium">异常</span>}
        </div>
        <div className="mt-1 flex items-baseline gap-1">
          <span className="text-xl font-bold text-[#1C1C1E]">
            {card.latest != null ? card.latest.toFixed(1) : '--'}
          </span>
          {unit && <span className="text-[10px] text-[#8E8E93]/70">{unit}</span>}
        </div>
        {trend != null && (
          <span className={`text-[11px] font-medium ${trendColor}`}>
            {trend > 0 ? '↑' : '↓'} {Math.abs(trend).toFixed(1)}
          </span>
        )}
        <Sparkline points={sparkPoints} color={color} height={28} />
      </div>
    </Link>
  );
}

export function MetricCardCompact({ card, reportId }: { card: OverviewCard; reportId: string }) {
  const label = METRIC_LABELS[card.metricKey] || card.label || card.metricKey;
  const unit = card.unit || METRIC_UNITS[card.metricKey] || '';
  return (
    <Link
      href={`/reports/${reportId}/metrics/${card.metricKey}`}
      className="block bg-[#F2F2F7] rounded-xl border border-black/5 px-3 py-2 hover:bg-black/[0.02] transition-colors"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs text-[#8E8E93]">{label}</span>
        <span className="text-sm font-semibold text-[#3A3A3C]">
          {card.latest != null ? card.latest.toFixed(1) : '--'}
          {unit && <span className="text-xs text-[#8E8E93]/70 ml-0.5">{unit}</span>}
        </span>
      </div>
    </Link>
  );
}
