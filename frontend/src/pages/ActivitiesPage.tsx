import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LoadError } from '../components/common/LoadError';
import { PageIntro } from '../components/common/PageIntro';
import {
  createActivity,
  getActivities,
  getActivityEvents,
  getActivityParticipants,
  type ActivityRequest,
} from '../services/activitySurveyService';
import type { Activity, EventItem, Participant } from '../types';
import { formatLocalDateTime } from '../utils/date';

export function ActivitiesPage() {
  const [activities, setActivities] = useState<Activity[]>([]);
  const [events, setEvents] = useState<EventItem[]>([]);
  const [participants, setParticipants] = useState<Participant[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const loadActivities = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setActivities(await getActivities());
    } catch {
      setActivities([]);
      setError('No se pudieron cargar los datos.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => { void loadActivities(); }, [loadActivities]);

  useEffect(() => {
    let active = true;
    Promise.all([getActivityEvents(), getActivityParticipants()])
      .then(([eventOptions, participantOptions]) => {
        if (active) {
          setEvents(eventOptions);
          setParticipants(participantOptions);
        }
      })
      .catch(() => {
        if (active) {
          setFormError('No se pudieron cargar los eventos o participantes para registrar actividades.');
        }
      });
    return () => {
      active = false;
    };
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const occurredAt = new Date(String(formData.get('occurredAt') ?? ''));
    if (Number.isNaN(occurredAt.getTime())) {
      setFormError('Indica una fecha y hora válidas para la actividad.');
      return;
    }
    const participantId = String(formData.get('participantId') ?? '').trim();
    const request: ActivityRequest = {
      eventId: Number(formData.get('eventId')),
      ...(participantId ? { participantId: Number(participantId) } : {}),
      type: String(formData.get('type') ?? ''),
      quantity: Number(formData.get('quantity')),
      result: String(formData.get('result') ?? '').trim(),
      occurredAt: occurredAt.toISOString(),
    };
    setIsSubmitting(true);
    setFormError(null);
    setSuccess(null);
    try {
      await createActivity(request);
      setSuccess('Actividad, sampling u observación registrada correctamente.');
      form.reset();
      await loadActivities();
    } catch (submitError) {
      setFormError(submitError instanceof Error ? submitError.message : 'No se pudo registrar la actividad.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      <PageIntro
        eyebrow="Actividades del evento"
        title="Experiencias en marcha"
        description="Registra dinámicas, degustaciones, sampling y observaciones; consulta lo que ya se reportó."
      />
      {events.length > 0 ? (
        <form className="coke-panel grid gap-4 p-5 md:grid-cols-2" onSubmit={handleSubmit}>
          <h2 className="md:col-span-2 text-lg font-bold text-slate-900">Registrar actividad u observación</h2>
          <label className="text-sm font-medium text-slate-700">Evento
            <select name="eventId" required defaultValue="" className="coke-input mt-1 w-full">
              <option value="" disabled>Selecciona un evento</option>
              {events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}
            </select>
          </label>
          <label className="text-sm font-medium text-slate-700">Tipo
            <select name="type" required defaultValue="DYNAMIC" className="coke-input mt-1 w-full">
              <option value="DYNAMIC">Dinámica</option>
              <option value="SAMPLING">Degustación / sampling</option>
              <option value="INTERACTION">Interacción</option>
              <option value="OBSERVATION">Observación</option>
            </select>
          </label>
          <label className="text-sm font-medium text-slate-700">Participante (opcional)
            <select name="participantId" defaultValue="" className="coke-input mt-1 w-full">
              <option value="">Sin participante asociado</option>
              {participants.map((participant) => (
                <option key={participant.id} value={participant.id}>
                  {participant.firstName} {participant.lastName} · {participant.email}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-medium text-slate-700">Cantidad / resultado cuantificable
            <input name="quantity" type="number" min="0" step="1" required defaultValue="0" className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">Fecha y hora
            <input name="occurredAt" type="datetime-local" required className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700 md:col-span-2">Resultado u observación
            <textarea name="result" required rows={3} maxLength={2000} className="coke-input mt-1 w-full" />
          </label>
          {formError ? <p role="alert" className="md:col-span-2 text-sm text-red-700">{formError}</p> : null}
          {success ? <p role="status" className="md:col-span-2 text-sm text-emerald-800">{success}</p> : null}
          <button type="submit" disabled={isSubmitting} className="coke-button md:col-span-2 md:justify-self-start">
            {isSubmitting ? 'Guardando…' : 'Registrar'}
          </button>
        </form>
      ) : null}
      {events.length === 0 && formError ? <p role="alert" className="coke-panel p-4 text-red-700">{formError}</p> : null}
      {isLoading ? <p role="status" className="coke-panel p-5 text-slate-600">Cargando actividades…</p> : null}
      {!isLoading && error ? <LoadError onRetry={() => void loadActivities()} /> : null}
      {!isLoading && !error && activities.length === 0 ? <p className="coke-panel p-5 text-slate-600">Todavía no hay actividades registradas.</p> : null}
      {!isLoading && !error && activities.length > 0 ? (
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {activities.map((activity) => (
            <article key={activity.id} className="coke-panel overflow-hidden p-5 sm:p-6">
              <div className="mb-5 flex items-center justify-between gap-3">
                <span className="inline-flex min-h-7 items-center rounded-full bg-red-50 px-3 text-[10px] font-bold uppercase tracking-[0.12em] text-red-700">{activity.type}</span>
                <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">Evento #{activity.eventId}</span>
              </div>
              <h3 className="text-xl font-bold leading-snug text-slate-950">{activity.name}</h3>
              {activity.result ? <p className="mt-2 min-h-10 text-sm leading-relaxed text-slate-600">{activity.result}</p> : null}
              <dl className="mt-5 space-y-3 border-t border-slate-100 pt-4 text-sm">
                <div className="flex justify-between gap-3"><dt className="text-slate-500">Inicio</dt><dd className="text-right font-medium text-slate-800">{formatLocalDateTime(activity.startAt)}</dd></div>
                <div className="flex justify-between gap-3"><dt className="text-slate-500">Fin</dt><dd className="text-right font-medium text-slate-800">{formatLocalDateTime(activity.endAt)}</dd></div>
                {activity.quantity !== undefined ? <div className="flex justify-between gap-3"><dt className="text-slate-500">Cantidad</dt><dd className="text-right font-semibold text-slate-800">{activity.quantity}</dd></div> : null}
                <div className="flex justify-between gap-3"><dt className="text-slate-500">Estado</dt><dd className="font-semibold text-emerald-700">{activity.status}</dd></div>
              </dl>
            </article>
          ))}
        </div>
      ) : null}
    </div>
  );
}
