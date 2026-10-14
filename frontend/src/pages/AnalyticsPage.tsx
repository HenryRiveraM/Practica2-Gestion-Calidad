import { useEffect, useRef, useState } from 'react';
import { PeakAttendanceChart } from '../components/charts/Charts';
import { LoadError } from '../components/common/LoadError';
import { PageIntro } from '../components/common/PageIntro';
import { downloadEventReport, getCampaigns, getEvents, getEventImpactScore, getEventInsights, getOverview } from '../services/dashboardService';
import type { AnalyticsOverview, Campaign, EventImpactScore, EventInsight, EventItem } from '../types';

export function AnalyticsPage() {
  const [events, setEvents] = useState<EventItem[]>([]);
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
  const [impactScore, setImpactScore] = useState<EventImpactScore | null>(null);
  const [eventInsights, setEventInsights] = useState<EventInsight | null>(null);
  const [eventId, setEventId] = useState('');
  const [campaignId, setCampaignId] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [optionsError, setOptionsError] = useState<string | null>(null);
  const [optionsRetry, setOptionsRetry] = useState(0);
  const [overviewRetry, setOverviewRetry] = useState(0);
  const [impactRetry, setImpactRetry] = useState(0);
  const [isImpactLoading, setIsImpactLoading] = useState(false);
  const [impactError, setImpactError] = useState(false);
  const [isInsightsLoading, setIsInsightsLoading] = useState(false);
  const [insightsError, setInsightsError] = useState<string | null>(null);
  const insightRequestId = useRef(0);
  const [isReportLoading, setIsReportLoading] = useState(false);
  const [reportError, setReportError] = useState<string | null>(null);
  const [reportSuccess, setReportSuccess] = useState(false);

  useEffect(() => {
    let active = true;

    Promise.all([getEvents(), getCampaigns()])
      .then(([eventOptions, campaignOptions]) => {
        if (active) {
          setEvents(eventOptions);
          setCampaigns(campaignOptions);
        }
      })
      .catch(() => {
        if (active) {
          setOptionsError('No se pudieron cargar los datos.');
        }
      });

    return () => {
      active = false;
    };
  }, [optionsRetry]);

  useEffect(() => {
    let active = true;

    if (startDate && endDate && startDate > endDate) {
      setOverview(null);
      setError('La fecha inicial no puede ser posterior a la fecha final.');
      setIsLoading(false);
      return () => {
        active = false;
      };
    }

    setIsLoading(true);
    setError(null);

    getOverview({
      ...(eventId ? { eventId: Number(eventId) } : {}),
      ...(campaignId ? { campaignId: Number(campaignId) } : {}),
      ...(startDate ? { startDate } : {}),
      ...(endDate ? { endDate } : {}),
    })
      .then((data) => {
        if (active) {
          setOverview(data);
        }
      })
      .catch(() => {
        if (active) {
          setOverview(null);
          setError('No se pudieron cargar los datos.');
        }
      })
      .finally(() => {
        if (active) {
          setIsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [eventId, campaignId, startDate, endDate, overviewRetry]);

  useEffect(() => {
    let active = true;
    if (!eventId) {
      setImpactScore(null);
      setImpactError(false);
      setIsImpactLoading(false);
      return () => {
        active = false;
      };
    }

    setIsImpactLoading(true);
    setImpactError(false);
    setImpactScore(null);
    getEventImpactScore(Number(eventId))
      .then((data) => {
        if (active) {
          setImpactScore(data);
        }
      })
      .catch(() => {
        if (active) {
          setImpactScore(null);
          setImpactError(true);
        }
      })
      .finally(() => {
        if (active) {
          setIsImpactLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [eventId, impactRetry]);

  async function generateEventInsights() {
    if (!eventId || isInsightsLoading) {
      return;
    }
    const requestId = ++insightRequestId.current;
    setIsInsightsLoading(true);
    setInsightsError(null);
    setEventInsights(null);
    try {
      const result = await getEventInsights(Number(eventId));
      if (insightRequestId.current === requestId) {
        setEventInsights(result);
      }
    } catch (error: unknown) {
      if (insightRequestId.current === requestId) {
        setInsightsError(getApiErrorMessage(error));
      }
    } finally {
      if (insightRequestId.current === requestId) {
        setIsInsightsLoading(false);
      }
    }
  }

  async function exportEventReport() {
    if (!eventId || isReportLoading) {
      return;
    }
    setIsReportLoading(true);
    setReportError(null);
    setReportSuccess(false);
    try {
      const report = await downloadEventReport(Number(eventId));
      const url = URL.createObjectURL(report);
      const link = document.createElement('a');
      link.href = url;
      link.download = `evento-${eventId}-reporte.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setReportSuccess(true);
    } catch (error: unknown) {
      setReportError(getApiErrorMessage(error));
    } finally {
      setIsReportLoading(false);
    }
  }

  const hasAnalytics = overview !== null;
  const isEmpty = overview !== null
    && overview.registered === 0
    && overview.attendees === 0
    && overview.conversions === 0
    && overview.new_participants === 0
    && overview.recurrent_participants === 0
    && overview.average_rating == null
    && overview.nps == null
    && (overview.peakAttendanceHours ?? []).length === 0;

  return (
    <>
      <div className="space-y-6">
        <PageIntro
          eyebrow="Event Intelligence"
          title="Analítica de eventos"
          description="Filtra por evento, campaña o periodo para entender el rendimiento de cada experiencia."
        />
        <section className="coke-panel grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-4" aria-label="Filtros de analítica">
          <label className="text-sm font-medium text-slate-700">
            Evento
            <select value={eventId} onChange={(event) => {
              insightRequestId.current += 1;
              setEventId(event.target.value);
              setEventInsights(null);
              setInsightsError(null);
              setIsInsightsLoading(false);
            }} className="coke-input mt-1 w-full">
              <option value="">Todos los eventos</option>
              {events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}
            </select>
          </label>
          <label className="text-sm font-medium text-slate-700">
            Campaña
            <select value={campaignId} onChange={(event) => setCampaignId(event.target.value)} className="coke-input mt-1 w-full">
              <option value="">Todas las campañas</option>
              {campaigns.map((campaign) => <option key={campaign.id} value={campaign.id}>{campaign.name}</option>)}
            </select>
          </label>
          <label className="text-sm font-medium text-slate-700">
            Fecha inicial
            <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">
            Fecha final
            <input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} className="coke-input mt-1 w-full" />
          </label>
          {eventId ? (
            <div className="flex flex-col items-start gap-2 md:col-span-2 xl:col-span-4">
              <button
                type="button"
                onClick={exportEventReport}
                disabled={isReportLoading}
                className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:cursor-wait disabled:opacity-60"
              >
                {isReportLoading ? 'Generando reporte…' : 'Exportar reporte CSV'}
              </button>
              {reportError ? <p role="alert" className="text-sm text-red-700">No se pudo descargar el reporte: {reportError}</p> : null}
              {reportSuccess ? <p role="status" className="text-sm text-emerald-700">Reporte descargado correctamente.</p> : null}
            </div>
          ) : null}
          {optionsError ? <div className="md:col-span-2 xl:col-span-4"><LoadError onRetry={() => setOptionsRetry((attempt) => attempt + 1)} /></div> : null}
        </section>

        <ImpactScorePanel
          selectedEventId={eventId}
          impactScore={impactScore}
          isLoading={isImpactLoading}
          hasError={impactError}
          onRetry={() => setImpactRetry((attempt) => attempt + 1)}
        />
        <EventInsightsPanel
          selectedEventId={eventId}
          data={eventInsights}
          isLoading={isInsightsLoading}
          hasError={insightsError}
          onGenerate={generateEventInsights}
        />

        {isLoading ? <p role="status" className="rounded-xl bg-white p-5 text-slate-600">Cargando analytics…</p> : null}
        {!isLoading && error === 'La fecha inicial no puede ser posterior a la fecha final.' ? (
          <p role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-amber-800">{error}</p>
        ) : null}
        {!isLoading && error && error !== 'La fecha inicial no puede ser posterior a la fecha final.' ? (
          <LoadError onRetry={() => setOverviewRetry((attempt) => attempt + 1)} />
        ) : null}
        {!isLoading && !error && isEmpty ? (
          <p className="rounded-xl border border-slate-200 bg-white p-5 text-slate-600">No hay datos para los filtros seleccionados.</p>
        ) : null}

        {!isLoading && !error && hasAnalytics ? (
          <>
            <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Indicadores">
              <Metric label="Registrados" value={overview?.registered.toLocaleString('es-ES') ?? '0'} />
              <Metric label="Asistentes" value={overview?.attendees.toLocaleString('es-ES') ?? '0'} />
              <Metric label="Tasa de asistencia" value={formatPercent(overview?.attendance_rate ?? null)} />
              <Metric label="Conversiones" value={overview?.conversions.toLocaleString('es-ES') ?? '0'} />
              <Metric label="Tasa de conversión" value={formatPercent(overview?.conversion_rate ?? null)} />
              <Metric label="Participantes nuevos" value={overview?.new_participants.toLocaleString('es-ES') ?? '0'} />
              <Metric label="Participantes recurrentes" value={overview?.recurrent_participants.toLocaleString('es-ES') ?? '0'} />
              <Metric label="NPS" value={formatScore(overview?.nps ?? null)} />
              <Metric label="Calificación promedio" value={formatScore(overview?.average_rating ?? null, '/5')} />
            </section>

            {isEmpty ? (
              <p className="rounded-xl border border-slate-200 bg-white p-5 text-slate-600">No hay datos para los filtros seleccionados.</p>
            ) : (overview?.peakAttendanceHours ?? []).length > 0 ? (
              <PeakAttendanceChart data={overview?.peakAttendanceHours ?? []} />
            ) : (
              <p className="rounded-xl border border-slate-200 bg-white p-5 text-slate-600">Aún no hay check-ins registrados en los horarios seleccionados.</p>
            )}
          </>
        ) : null}
      </div>
    </>
  );
}

function EventInsightsPanel({
  selectedEventId,
  data,
  isLoading,
  hasError,
  onGenerate,
}: {
  selectedEventId: string;
  data: EventInsight | null;
  isLoading: boolean;
  hasError: string | null;
  onGenerate: () => void;
}) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-soft" aria-labelledby="event-insights-heading">
      <div className="mb-4">
        <h2 id="event-insights-heading" className="text-lg font-semibold text-slate-900">Insights del evento</h2>
        <p className="mt-1 text-sm text-slate-600">Observaciones basadas en registros y métricas reales del evento seleccionado.</p>
      </div>
      {!selectedEventId ? <p className="text-sm text-slate-500">Selecciona un evento en los filtros para consultar sus insights.</p> : null}
      {selectedEventId ? (
        <button
          type="button"
          onClick={onGenerate}
          disabled={isLoading}
          className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:cursor-wait disabled:opacity-60"
        >
          {isLoading ? 'Generando insights…' : data?.generatedByAI ? 'Generar nuevamente' : 'Generar insights con IA'}
        </button>
      ) : null}
      {isLoading ? <p role="status" className="mt-4 text-sm text-slate-600">Generando insights con Gemini…</p> : null}
      {hasError ? (
        <p role="alert" className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          Error de IA: {hasError}
        </p>
      ) : null}
      {!isLoading && data?.status === 'NOT_CONFIGURED' ? (
        <p role="status" className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">{data.message}</p>
      ) : null}
      {!isLoading && data?.status === 'INSUFFICIENT_DATA' ? (
        <p role="status" className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">{data.message}</p>
      ) : null}
      {!isLoading && data?.generatedByAI ? (
        <>
          <p className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900">Insights generados por {data.provider} · {data.model}</p>
          {data.summary ? <InsightList title="Resumen ejecutivo" items={[data.summary]} /> : null}
          <InsightList title="Insights" items={data.insights} />
          <InsightList title="Anomalías" items={data.anomalies} />
          <InsightList title="Recomendaciones" items={data.recommendations} />
        </>
      ) : null}
    </section>
  );
}

function InsightList({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) {
    return null;
  }
  return (
    <section className="mt-4">
      <h3 className="mb-2 font-semibold text-slate-800">{title}</h3>
      <ul className="list-disc space-y-2 pl-5 text-sm text-slate-700">
        {items.map((item, index) => <li key={`${title}-${index}`}>{item}</li>)}
      </ul>
    </section>
  );
}

function getApiErrorMessage(error: unknown): string {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = error.response;
    if (typeof response === 'object' && response !== null && 'data' in response) {
      const data = response.data;
      if (typeof data === 'object' && data !== null && 'message' in data && typeof data.message === 'string') {
        return data.message;
      }
    }
  }
  return 'No se pudieron generar insights con Gemini. Intenta nuevamente.';
}

function ImpactScorePanel({
  selectedEventId,
  impactScore,
  isLoading,
  hasError,
  onRetry,
}: {
  selectedEventId: string;
  impactScore: EventImpactScore | null;
  isLoading: boolean;
  hasError: boolean;
  onRetry: () => void;
}) {
  const metricLabels: Record<keyof EventImpactScore['metrics'], string> = {
    attendance: 'Asistencia',
    engagement: 'Engagement',
    satisfaction: 'Satisfacción',
    conversion: 'Conversión',
    recurrence: 'Recurrencia',
  };

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-soft" aria-labelledby="impact-score-heading">
      <div className="mb-4">
        <h2 id="impact-score-heading" className="text-lg font-semibold text-slate-900">Impact Score del evento</h2>
        <p className="mt-1 text-sm text-slate-600">Selecciona un evento para calcular su resultado con métricas disponibles.</p>
      </div>
      {!selectedEventId ? <p className="text-sm text-slate-500">Selecciona un evento en los filtros para ver el desglose.</p> : null}
      {isLoading ? <p role="status" className="text-sm text-slate-600">Calculando Impact Score…</p> : null}
      {hasError ? <LoadError onRetry={onRetry} /> : null}
      {!isLoading && !hasError && impactScore ? (
        <>
          <div className="mb-5 flex flex-wrap items-end justify-between gap-3 rounded-xl bg-slate-50 p-4">
            <div>
              <p className="text-sm text-slate-600">Puntaje compuesto</p>
              <p className="mt-1 text-4xl font-bold text-slate-900">
                {impactScore.score == null ? 'Sin datos' : `${impactScore.score.toFixed(1)} / 100`}
              </p>
            </div>
            <span className="rounded-full bg-red-100 px-3 py-1 text-xs font-semibold text-red-700">
              {impactScore.status === 'CALCULATED'
                ? 'Completo'
                : impactScore.status === 'PARTIAL_DATA'
                  ? 'Datos parciales'
                  : 'Información insuficiente'}
            </span>
          </div>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
            {(Object.keys(metricLabels) as Array<keyof EventImpactScore['metrics']>).map((key) => {
              const value = impactScore.metrics?.[key];
              const appliedWeight = impactScore.appliedWeights?.[key];
              return (
                <article key={key} className="rounded-xl border border-slate-200 p-4">
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="font-semibold text-slate-800">{metricLabels[key]}</h3>
                    <span className="text-xs text-slate-500">
                      {appliedWeight === undefined ? 'No ponderada' : `${(appliedWeight * 100).toFixed(0)}%`}
                    </span>
                  </div>
                  <p className="mt-3 text-2xl font-bold text-slate-900">{value == null ? 'Sin datos' : `${value.toFixed(1)}%`}</p>
                  {value != null ? (
                    <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full rounded-full bg-red-600" style={{ width: `${Math.max(0, Math.min(value, 100))}%` }} />
                    </div>
                  ) : null}
                  <p className="mt-3 text-xs leading-relaxed text-slate-500">{impactScore.metricDefinitions?.[key] ?? 'Sin definición disponible.'}</p>
                </article>
              );
            })}
          </div>
          <p className="mt-4 text-xs text-slate-500">
            Fórmula: promedio ponderado de las métricas disponibles; las ponderaciones restantes se normalizan. Pesos base:
            {' '}{Object.entries(impactScore.weights ?? {}).map(([key, weight]) => (
              typeof weight === 'number'
                ? `${metricLabels[key as keyof EventImpactScore['metrics']] ?? key} ${(weight * 100).toFixed(0)}%`
                : null
            )).filter((weight): weight is string => weight !== null).join(' · ')}.
            {(impactScore.missingMetrics ?? []).length > 0 ? ` Sin datos: ${(impactScore.missingMetrics ?? []).map((key) => metricLabels[key as keyof EventImpactScore['metrics']] ?? key).join(', ')}.` : ''}
          </p>
        </>
      ) : null}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <article className="coke-panel min-h-28 p-5">
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-3 text-3xl font-bold text-slate-900">{value}</p>
    </article>
  );
}

function formatPercent(value: number | null | undefined): string {
  return value == null ? 'Sin datos' : `${value.toFixed(1)}%`;
}

function formatScore(value: number | null | undefined, suffix = ''): string {
  return value == null ? 'Sin datos' : `${value.toFixed(1)}${suffix}`;
}
