export function Sparkline({ points, color = '#8E8E93', height = 32 }: { points: (number | null)[]; color?: string; height?: number }) {
  const valid = points.filter((p): p is number => p != null);
  if (valid.length < 2) return <div style={{ height }} />;

  const min = Math.min(...valid);
  const max = Math.max(...valid);
  const range = max - min || 1;
  const width = valid.length * 4;
  const pad = 2;

  const coords = valid.map((v, i) => {
    const x = pad + (i / (valid.length - 1)) * (width - pad * 2);
    const y = height - pad - ((v - min) / range) * (height - pad * 2);
    return `${x},${y}`;
  }).join(' ');

  return (
    <div className="mt-1" style={{ height }}>
      <svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none">
        <polyline points={coords} fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" opacity={0.7} />
      </svg>
    </div>
  );
}
