type MetricCardProps = {
  label: string;
  value: string;
  change?: string;
  accent?: 'red' | 'dark' | 'slate';
};

const accents = {
  red: 'bg-white text-red-700 ring-slate-200',
  dark: 'bg-white text-slate-950 ring-slate-200',
  slate: 'bg-white text-slate-700 ring-slate-200',
};

export function MetricCard({ label, value, change, accent = 'red' }: MetricCardProps) {
  return (
    <div className={`min-h-28 rounded-2xl px-5 py-4 ring-1 shadow-soft ${accents[accent]}`}>
      <p className="text-[13px] font-medium text-slate-500">{label}</p>
      <div className="mt-2 flex items-end justify-between gap-3">
        <span className="text-3xl font-bold tracking-tight">{value}</span>
        {change ? <span className="text-xs font-semibold uppercase tracking-[0.14em] text-current/70">{change}</span> : null}
      </div>
    </div>
  );
}
