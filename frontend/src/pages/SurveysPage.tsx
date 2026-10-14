import { useEffect, useState, type FormEvent } from 'react';
import { PageIntro } from '../components/common/PageIntro';
import {
  getSurveyEvents,
  getSurveyParticipants,
  submitSurvey,
  type SurveyResponse,
} from '../services/activitySurveyService';
import type { EventItem, Participant } from '../types';

export function SurveysPage() {
  const [events, setEvents] = useState<EventItem[]>([]);
  const [participants, setParticipants] = useState<Participant[]>([]);
  const [optionsLoading, setOptionsLoading] = useState(true);
  const [optionsError, setOptionsError] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<SurveyResponse | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([getSurveyEvents(), getSurveyParticipants()])
      .then(([eventOptions, participantOptions]) => {
        if (active) {
          setEvents(eventOptions);
          setParticipants(participantOptions);
        }
      })
      .catch(() => {
        if (active) {
          setOptionsError(true);
        }
      })
      .finally(() => {
        if (active) {
          setOptionsLoading(false);
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
    setError(null);
    setSuccess(null);
    setIsSubmitting(true);
    try {
      const response = await submitSurvey({
        eventId: Number(formData.get('eventId')),
        participantId: Number(formData.get('participantId')),
        rating: Number(formData.get('rating')),
        nps: Number(formData.get('nps')),
        comment: String(formData.get('comment') ?? '').trim(),
      });
      setSuccess(response);
      form.reset();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'No se pudo enviar la encuesta.');
    } finally {
      setIsSubmitting(false);
    }
  }

  const hasOptions = events.length > 0 && participants.length > 0;

  return (
    <div className="space-y-6">
      <PageIntro
        eyebrow="Voz del participante"
        title="Registrar encuesta"
        description="Registra la satisfacción y la intención de recomendación vinculadas a un participante y evento."
      />
      {optionsLoading ? <p role="status" className="coke-panel p-5 text-slate-600">Cargando eventos y participantes…</p> : null}
      {!optionsLoading && optionsError ? (
        <p role="alert" className="coke-panel p-5 text-red-700">No se pudieron cargar los eventos o participantes. Actualiza la página e inténtalo nuevamente.</p>
      ) : null}
      {!optionsLoading && !optionsError && !hasOptions ? (
        <p className="coke-panel p-5 text-slate-600">
          No hay eventos o participantes disponibles para asociar una encuesta.
        </p>
      ) : null}
      {!optionsLoading && !optionsError && hasOptions ? (
        <form className="coke-panel grid gap-4 p-5 md:grid-cols-2" onSubmit={handleSubmit}>
          <label className="text-sm font-medium text-slate-700">Evento
            <select name="eventId" required defaultValue="" className="coke-input mt-1 w-full">
              <option value="" disabled>Selecciona un evento</option>
              {events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}
            </select>
          </label>
          <label className="text-sm font-medium text-slate-700">Participante
            <select name="participantId" required defaultValue="" className="coke-input mt-1 w-full">
              <option value="" disabled>Selecciona un participante</option>
              {participants.map((participant) => (
                <option key={participant.id} value={participant.id}>
                  {participant.firstName} {participant.lastName} · {participant.email}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-medium text-slate-700">Satisfacción (1–5)
            <input name="rating" type="number" min="1" max="5" step="1" required className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">NPS (0–10)
            <input name="nps" type="number" min="0" max="10" step="1" required className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700 md:col-span-2">Comentario (opcional)
            <textarea name="comment" maxLength={2000} rows={4} className="coke-input mt-1 w-full" />
          </label>
          {error ? <p role="alert" className="md:col-span-2 text-sm text-red-700">{error}</p> : null}
          {success ? (
            <p role="status" className="md:col-span-2 text-sm text-emerald-800">
              Encuesta registrada correctamente (#{success.id}).
            </p>
          ) : null}
          <button type="submit" disabled={isSubmitting} className="coke-button md:col-span-2 md:justify-self-start">
            {isSubmitting ? 'Enviando encuesta…' : 'Registrar encuesta'}
          </button>
        </form>
      ) : null}
    </div>
  );
}
