import { useState } from 'react';
import type { DashboardSummary } from '../../types';

const number = new Intl.NumberFormat('es-BO', { maximumFractionDigits: 1 });
const percent = (value: number) => `${number.format(value)}%`;

type RankingItem = { id: string | number; name: string; value: number };

function RankingCard({ title, eyebrow, data, metric, metricLabel, note, percentage = false, expandable = false }: {
  title: string;
  eyebrow: string;
  data: RankingItem[];
  metric: string;
  metricLabel: string;
  note: string;
  percentage?: boolean;
  expandable?: boolean;
}) {
  const [expanded, setExpanded] = useState(false);
  const sorted = [...data].sort((a, b) => b.value - a.value || a.name.localeCompare(b.name));
  const visible = expandable && !expanded ? sorted.slice(0, 5) : sorted;
  const maximum = percentage ? 100 : Math.max(1, ...data.map(({ value }) => value));

  return (
    <section className="brand-chart-card">
      <header className="brand-chart-heading">
        <div>
          <p className="brand-chart-eyebrow">{eyebrow}</p>
          <h3>{title}</h3>
        </div>
        <span className="brand-chart-emblem" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none"><path d="M6 23V16M16 23V9M26 23V4" stroke="currentColor" strokeWidth="4" strokeLinecap="round" /></svg>
        </span>
      </header>
      <div className="brand-chart-summary"><strong>{metric}</strong><span>{metricLabel}</span></div>
      {data.length ? (
        <ol className="brand-ranking">
          {visible.map((item, index) => (
            <li key={item.id}>
              <span className={`brand-ranking-position${index === 0 ? ' is-first' : ''}`} aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
              <div className="brand-ranking-item">
                <div className="brand-ranking-label"><span>{item.name}</span><strong>{percentage ? percent(item.value) : number.format(item.value)}</strong></div>
                <div className="brand-ranking-track" aria-hidden="true"><span style={{ width: `${Math.max(0, Math.min(100, item.value / maximum * 100))}%` }} /></div>
              </div>
            </li>
          ))}
        </ol>
      ) : <p className="brand-chart-empty">Aún no hay datos disponibles.</p>}
      <footer className="brand-chart-footer">
        <span>{note}</span>
        {expandable && sorted.length > 5 ? <button type="button" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>{expanded ? 'Ver los 5 primeros' : `Ver los ${sorted.length} productos`}<span aria-hidden="true"> ↗</span></button> : null}
      </footer>
    </section>
  );
}

function AttendanceCard({ overview }: Pick<DashboardSummary, 'overview'>) {
  const rate = overview.attendance_rate ?? (overview.registered > 0 ? overview.attendees / overview.registered * 100 : null);
  const ringRate = Math.max(0, Math.min(100, rate ?? 0));

  return (
    <section className="brand-chart-card brand-attendance-card">
      <header className="brand-chart-heading">
        <div><p className="brand-chart-eyebrow">De la invitación a la experiencia</p><h3>Registrados vs. asistentes</h3></div>
        <span className="brand-live-label">Check-in</span>
      </header>
      <div className="brand-attendance-visual">
        <div className="brand-attendance-ring" aria-hidden="true">
          <svg viewBox="0 0 220 220">
            <circle cx="110" cy="110" r="96" fill="none" stroke="#383a3d" strokeWidth="15" />
            <circle cx="110" cy="110" r="96" fill="none" stroke="var(--coke-red)" strokeWidth="15" pathLength="100" strokeDasharray={`${ringRate} ${100 - ringRate}`} transform="rotate(-90 110 110)" />
          </svg>
          <div className="brand-ring-center"><img src="/brand/coca-cola-logo-white.png" alt="" /><span>Momentos compartidos</span></div>
        </div>
        <div className="brand-attendance-rate"><strong>{rate == null ? '—' : percent(rate)}</strong><span>Tasa de asistencia</span><p>{number.format(overview.attendees)} asistencias de {number.format(overview.registered)} registros.</p></div>
      </div>
      <dl className="brand-attendance-counts">
        <div><dt><i className="is-registered" aria-hidden="true" />Registrados</dt><dd>{number.format(overview.registered)}</dd></div>
        <div><dt><i className="is-attended" aria-hidden="true" />Asistentes</dt><dd>{number.format(overview.attendees)}</dd></div>
      </dl>
      <footer className="brand-chart-footer">Asistencias registradas mediante check-in en todos los eventos.</footer>
    </section>
  );
}

export function DashboardCharts({ summary }: { summary: DashboardSummary }) {
  // The API returns a row per event and product; combine events for this global ranking.
  const productTotals = new Map<string, number>();
  summary.productRanking.forEach(({ product_name, total_interactions }) => {
    productTotals.set(product_name, (productTotals.get(product_name) ?? 0) + total_interactions);
  });
  const products = Array.from(productTotals, ([name, value]) => ({ id: name, name, value }));
  const interactions = products.reduce((total, product) => total + product.value, 0);
  const registrations = summary.events.map((event) => ({ id: event.event_id, name: event.event_name, value: event.registered }));
  const conversions = summary.events.filter((event) => event.conversion_rate_pct != null).map((event) => ({ id: event.event_id, name: event.event_name, value: event.conversion_rate_pct }));

  return (
    <div className="brand-chart-grid">
      <RankingCard title="Registrados por evento" eyebrow="El alcance de cada encuentro" data={registrations} metric={number.format(summary.overview.registered)} metricLabel="registros en todos los eventos" note={`${registrations.length} eventos · Ordenados por número de registros`} />
      <AttendanceCard overview={summary.overview} />
      <RankingCard title="Productos con más interacciones" eyebrow="Sabores que conectan" data={products} metric={number.format(interactions)} metricLabel="interacciones con el portafolio" note="Interacciones acumuladas en todos los eventos" expandable />
      <RankingCard title="Conversión por evento" eyebrow="Experiencias que generan resultados" data={conversions} metric={number.format(summary.overview.conversions)} metricLabel="conversiones registradas" note="Tasa de conversión por evento · Escala de 0 a 100%" percentage />
    </div>
  );
}
