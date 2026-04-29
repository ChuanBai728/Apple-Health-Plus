'use client';

import { MetricPoint } from '@/lib/types';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Area } from 'recharts';

export function TrendChart({ data, label, height = 320 }: { data: MetricPoint[]; label: string; height?: number }) {
  if (!data || data.length === 0) {
    return <div className="text-center text-[#6E6E73] py-12">暂无数据</div>;
  }

  const chartData = data.map((p) => ({
    date: p.date?.substring(0, 10) || '',
    value: p.value,
  }));

  return (
    <div>
      <h3 className="text-base font-semibold text-[#1D1D1F] mb-4">{label}</h3>
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
          <defs>
            <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#3A7BFF" stopOpacity={0.15} />
              <stop offset="100%" stopColor="#3A7BFF" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" vertical={false} />
          <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#6E6E73' }} axisLine={false} tickLine={false} />
          <YAxis tick={{ fontSize: 11, fill: '#6E6E73' }} axisLine={false} tickLine={false} width={40} />
          <Tooltip
            contentStyle={{
              borderRadius: 12,
              border: '1px solid #E5E7EB',
              boxShadow: '0 4px 12px rgba(0,0,0,0.06)',
              fontSize: 13,
            }}
            labelStyle={{ color: '#6E6E73', marginBottom: 2 }}
          />
          <Area type="monotone" dataKey="value" fill="url(#chartGradient)" stroke="none" />
          <Line
            type="monotone"
            dataKey="value"
            stroke="#3A7BFF"
            strokeWidth={2.5}
            dot={false}
            activeDot={{ r: 5, fill: '#3A7BFF', stroke: '#fff', strokeWidth: 2 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
