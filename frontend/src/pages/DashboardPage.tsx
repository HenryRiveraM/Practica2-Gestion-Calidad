import { useCallback, useEffect, useState } from 'react';
import { DashboardCharts } from '../components/charts/DashboardCharts';
import { useRole } from '../components/layout/RoleContext';
import { LoadError } from '../components/common/LoadError';
import { MetricCard } from '../components/common/MetricCard';
import { PageIntro } from '../components/common/PageIntro';
import { getDashboardData } from '../services/dashboardService';
import type { DashboardSummary } from '../types';
import { formatLocalDate } from '../utils/date';

export function DashboardPage() {
  const { role, user } = useRole();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadDashboard = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setSummary(await getDashboardData());
    } catch {
      setSummary(null);
      setError('No se pudieron cargar los datos.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  if (isLoading) {
    return <p role="status" className="rounded-xl bg-white p-5 text-slate-600">Cargando datos…</p>;
  }

  if (error || !summary) {
    return <LoadError onRetry={() => void loadDashboard()} />;
  }

  const hasData = summary.events.length > 0
    || summary.recentEvents.length > 0
    || summary.overview.registered > 0
    || summary.overview.attendees > 0
    || summary.overview.conversions > 0
    || summary.productRanking.length > 0;
  if (!hasData) {
    return <p className="rounded-xl border border-slate-200 bg-white p-5 text-slate-600">{role === 'ORGANIZADOR' && !user?.eventIds.length ? 'Todavía no tienes eventos asignados. Tu administrador puede darte acceso desde Equipo.' : 'No hay datos disponibles.'}</p>;
  }

  return (
      <div className="space-y-6">
        <PageIntro
          eyebrow="Coca-Cola Event Intelligence"
          title={role === 'ORGANIZADOR' ? 'Tus eventos, de un vistazo' : 'Dashboard general'}
          description="Una visión clara de la asistencia, las conversiones y las experiencias de marca."
          actions={<img src="/brand/coca-cola-logo-red.webp" alt="Coca-Cola" className="brand-mark-red hidden md:block" />}
        />
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="Eventos" value={String(summary.events.length)} accent="red" />
          <MetricCard label="Participantes registrados" value={String(summary.overview.registered)} accent="dark" />
          <MetricCard label="Asistentes" value={String(summary.overview.attendees)} accent="slate" />
          <MetricCard label="Conversiones" value={String(summary.overview.conversions)} accent="red" />
        </div>

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-soft">
            <p className="text-sm text-slate-500">Tasa de asistencia</p>
            <p className="mt-3 text-3xl font-bold text-slate-900">{formatPercent(summary.overview.attendance_rate)}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-soft">
            <p className="text-sm text-slate-500">Tasa de conversión</p>
            <p className="mt-3 text-3xl font-bold text-slate-900">{formatPercent(summary.overview.conversion_rate)}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-soft">
            <p className="text-sm text-slate-500">Satisfacción</p>
            <p className="mt-3 text-3xl font-bold text-slate-900">{formatScore(summary.overview.average_rating, '/5')}</p>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-soft">
            <p className="text-sm text-slate-500">NPS</p>
            <p className="mt-3 text-3xl font-bold text-slate-900">{formatScore(summary.overview.nps)}</p>
          </div>
        </div>

        <DashboardCharts summary={summary} />

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-soft">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-lg font-semibold text-slate-900">Eventos recientes</h3>
            <span className="text-sm text-slate-500">Últimos 4</span>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="text-slate-500">
                <tr>
                  <th className="pb-3">Evento</th>
                  <th className="pb-3">Fecha</th>
                  <th className="pb-3">Lugar</th>
                  <th className="pb-3">Estado</th>
                </tr>
              </thead>
              <tbody>
                {summary.recentEvents.map((event) => (
                  <tr key={event.id} className="border-t border-slate-200">
                    <td className="py-3 font-medium text-slate-800">{event.name}</td>
                    <td className="py-3 text-slate-600">{formatLocalDate(event.eventDate)}</td>
                    <td className="py-3 text-slate-600">{event.location}</td>
                    <td className="py-3">
                      <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-semibold text-red-700">{event.status}</span>
                    </td>
                  </tr>
                ))}
                {summary.recentEvents.length === 0 ? (
                  <tr><td colSpan={4} className="py-6 text-center text-slate-500">No hay eventos registrados.</td></tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </div>
      </div>
  );
}

function formatPercent(value: number | null | undefined): string {
  return value == null ? 'Sin datos' : `${value.toFixed(1)}%`;
}

function formatScore(value: number | null | undefined, suffix = ''): string {
  return value == null ? 'Sin datos' : `${value.toFixed(1)}${suffix}`;
}
