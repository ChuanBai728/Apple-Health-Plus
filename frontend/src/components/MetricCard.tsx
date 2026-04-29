import { OverviewCard, METRIC_LABELS, METRIC_UNITS, getTrendColor } from '@/lib/types';
import Link from 'next/link';
import { Sparkline } from './Sparkline';

const ACCENT_COLORS: Record<string, string> = {
  activity: '#3b82f6', heart: '#ef4444', body: '#10b981', sleep_env: '#8b5cf6',
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
      className={`block bg-white rounded-xl border overflow-hidden hover:shadow-lg hover:-translate-y-0.5 transition-all ${
        card.anomaly ? 'border-amber-300 ring-1 ring-amber-200' : 'border-gray-200'
      }`}
    >
      <div className="h-1" style={{ backgroundColor: color }} />
      <div className="p-3">
        <div className="flex items-center justify-between">
          <span className="text-xs font-medium text-gray-500">{label}</span>
          {card.anomaly && <span className="text-[10px] bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-full font-medium">异常</span>}
        </div>
        <div className="mt-1 flex items-baseline gap-1">
          <span className="text-xl font-bold text-gray-900">
            {card.latest != null ? card.latest.toFixed(1) : '--'}
          </span>
          {unit && <span className="text-[10px] text-gray-400">{unit}</span>}
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
      className="block bg-gray-50 rounded-lg border border-gray-100 px-3 py-2 hover:bg-gray-100 transition-colors"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs text-gray-500">{label}</span>
        <span className="text-sm font-semibold text-gray-700">
          {card.latest != null ? card.latest.toFixed(1) : '--'}
          {unit && <span className="text-xs text-gray-400 ml-0.5">{unit}</span>}
        </span>
      </div>
    </Link>
  );
}
