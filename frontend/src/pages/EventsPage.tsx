import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useLocation } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { PublicEventAccess } from '../components/events/PublicEventAccess';
import { LoadError } from '../components/common/LoadError';
import { PageIntro } from '../components/common/PageIntro';
import { useRole } from '../components/layout/RoleContext';
import { getEvents } from '../services/dashboardService';
import { createEvent, updateEvent, type EventWriteRequest } from '../services/eventManagementService';
import { ApiRequestError, registerParticipant } from '../services/registrationService';
import type { RegistrationResponse } from '../services/registrationService';
import type { EventItem } from '../types';
import { formatLocalDate } from '../utils/date';

export function EventsPage() {
  const { role } = useRole();
  const location = useLocation();
  const isAdministrator = role === 'ADMINISTRADOR';
  const isRegistrationView = location.pathname === '/registration';
  const [events, setEvents] = useState<EventItem[]>([]);
  const [isLoadingEvents, setIsLoadingEvents] = useState(true);
  const [eventsError, setEventsError] = useState<string | null>(null);
  const [registrationEvent, setRegistrationEvent] = useState<EventItem | null>(null);
  const [registration, setRegistration] = useState<RegistrationResponse | null>(null);
  const [registrationError, setRegistrationError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isQrVisible, setIsQrVisible] = useState(true);
  const [isEventFormOpen, setIsEventFormOpen] = useState(false);
  const [editingEvent, setEditingEvent] = useState<EventItem | null>(null);
  const [isSavingEvent, setIsSavingEvent] = useState(false);
  const [eventSaveError, setEventSaveError] = useState<string | null>(null);
  const [eventSaveSuccess, setEventSaveSuccess] = useState<string | null>(null);

  const loadEvents = useCallback(async (background = false) => {
    if (!background) setIsLoadingEvents(true);
    setEventsError(null);
    try {
      setEvents(await getEvents());
    } catch {
      setEventsError('No se pudieron cargar los datos.');
    } finally {
      setIsLoadingEvents(false);
    }
  }, []);

  useEffect(() => {
    void loadEvents();
  }, [loadEvents]);

  async function handleRegistration(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!registrationEvent) {
      return;
    }

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get('email') ?? '').trim();
    setRegistrationError(null);
    setRegistration(null);
    if (!/^\S+@\S+\.\S+$/.test(email)) {
      setRegistrationError('Ingresa un correo válido, por ejemplo nombre@dominio.com (sin espacios).');
      return;
    }
    setIsSubmitting(true);

    try {
      const result = await registerParticipant({
        eventId: registrationEvent.id,
        firstName: String(formData.get('firstName') ?? '').trim(),
        lastName: String(formData.get('lastName') ?? '').trim(),
        email,
        phone: String(formData.get('phone') ?? '').trim(),
      });
      setRegistration(result);
    } catch (requestError) {
      setRegistrationError(requestError instanceof ApiRequestError
        ? requestError.message
        : 'No se pudieron cargar los datos.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleEventSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const campaignId = String(formData.get('campaignId') ?? '').trim();
    const budget = String(formData.get('budget') ?? '').trim();
    const expectedParticipants = String(formData.get('expectedParticipants') ?? '').trim();
    const request: EventWriteRequest = {
      name: String(formData.get('name') ?? '').trim(),
      type: String(formData.get('type') ?? '').trim(),
      eventDate: String(formData.get('eventDate') ?? ''),
      location: String(formData.get('location') ?? '').trim(),
      description: String(formData.get('description') ?? '').trim(),
      objective: String(formData.get('objective') ?? '').trim(),
      responsible: String(formData.get('responsible') ?? '').trim(),
      status: String(formData.get('status') ?? 'PLANNED'),
      ...(campaignId ? { campaignId: Number(campaignId) } : {}),
      ...(budget ? { budget: Number(budget) } : {}),
      ...(expectedParticipants ? { expectedParticipants: Number(expectedParticipants) } : {}),
    };

    setIsSavingEvent(true);
    setEventSaveError(null);
    setEventSaveSuccess(null);
    try {
      if (editingEvent) {
        await updateEvent(editingEvent.id, request);
        setEventSaveSuccess('Evento actualizado correctamente.');
      } else {
        await createEvent(request);
        setEventSaveSuccess('Evento creado correctamente.');
      }
      setEditingEvent(null);
      setIsEventFormOpen(false);
      await loadEvents();
    } catch (saveError) {
      setEventSaveError(saveError instanceof ApiRequestError
        ? saveError.message
        : 'No se pudo guardar el evento.');
    } finally {
      setIsSavingEvent(false);
    }
  }

  return (
    <div className="space-y-6">
      <PageIntro
        eyebrow={isAdministrator ? 'Administración de eventos' : isRegistrationView ? 'Registro de participantes' : 'Experiencias Coca-Cola'}
        title={isAdministrator ? 'Administrar eventos' : isRegistrationView ? 'Registrar participantes' : 'Eventos'}
        description={isAdministrator
          ? 'Crea eventos y actualiza sus responsables, objetivos e información operativa.'
          : isRegistrationView
            ? 'Selecciona un evento para registrar participantes y generar su QR de acceso.'
            : 'Consulta los eventos y sus resultados disponibles.'}
        actions={isAdministrator ? (
          <button
            type="button"
            className="coke-button"
            onClick={() => {
              setEditingEvent(null);
              setEventSaveError(null);
              setIsEventFormOpen((open) => !open);
            }}
          >
            {isEventFormOpen && !editingEvent ? 'Cancelar' : 'Crear evento'}
          </button>
        ) : undefined}
      />
      {isAdministrator && eventSaveSuccess ? <p role="status" className="coke-panel p-4 text-emerald-800">{eventSaveSuccess}</p> : null}
      {isAdministrator && isEventFormOpen ? (
        <form className="coke-panel grid gap-4 p-5 md:grid-cols-2" onSubmit={handleEventSave}>
          <h2 className="md:col-span-2 text-lg font-bold text-slate-900">{editingEvent ? 'Editar evento' : 'Crear evento'}</h2>
          <label className="text-sm font-medium text-slate-700">Nombre
            <input name="name" required maxLength={200} defaultValue={editingEvent?.name ?? ''} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">Tipo
            <input name="type" required maxLength={50} defaultValue={editingEvent?.type ?? ''} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">Fecha
            <input name="eventDate" type="date" required defaultValue={editingEvent?.eventDate?.slice(0, 10) ?? ''} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">Ubicación
            <input name="location" required maxLength={200} defaultValue={editingEvent?.location ?? ''} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">Responsable
            <input name="responsible" maxLength={150} defaultValue={editingEvent?.responsible ?? ''} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">Estado
            <select name="status" defaultValue={editingEvent?.status ?? 'PLANNED'} className="coke-input mt-1 w-full">
              <option value="PLANNED">Planificado</option>
              <option value="ACTIVE">Activo · Publicar y abrir inscripciones</option>
              <option value="COMPLETED">Completado</option>
              <option value="CANCELLED">Cancelado</option>
            </select>
          </label>
          <label className="text-sm font-medium text-slate-700">ID de campaña (opcional)
            <input name="campaignId" type="number" min="1" defaultValue={editingEvent?.campaignId ?? ''} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">Participantes esperados
            <input name="expectedParticipants" type="number" min="0" defaultValue={editingEvent?.expectedParticipants ?? ''} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">Presupuesto
            <input name="budget" type="number" min="0" step="0.01" defaultValue={editingEvent?.budget ?? ''} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700">Objetivo
            <input name="objective" defaultValue={editingEvent?.objective ?? ''} className="coke-input mt-1 w-full" />
          </label>
          <label className="text-sm font-medium text-slate-700 md:col-span-2">Descripción
            <textarea name="description" rows={3} defaultValue={editingEvent?.description ?? ''} className="coke-input mt-1 w-full" />
          </label>
          {eventSaveError ? <p role="alert" className="md:col-span-2 text-sm text-red-700">{eventSaveError}</p> : null}
          <div className="flex gap-3 md:col-span-2">
            <button type="submit" disabled={isSavingEvent} className="coke-button">
              {isSavingEvent ? 'Guardando…' : editingEvent ? 'Guardar cambios' : 'Crear evento'}
            </button>
            <button
              type="button"
              className="coke-button coke-button-secondary"
              onClick={() => {
                setIsEventFormOpen(false);
                setEditingEvent(null);
                setEventSaveError(null);
              }}
            >
              Cancelar
            </button>
          </div>
        </form>
      ) : null}
      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        {isLoadingEvents ? <p role="status" className="text-slate-600">Cargando eventos…</p> : null}
        {!isLoadingEvents && eventsError ? <LoadError onRetry={() => void loadEvents()} /> : null}
        {!isLoadingEvents && !eventsError && events.length === 0 ? <p className="text-slate-600">No hay eventos disponibles.</p> : null}
        {!isLoadingEvents && !eventsError ? events.map((event) => (
            <article key={event.id} className="overflow-hidden rounded-[20px] border border-white/80 bg-white/95 shadow-soft transition duration-200 hover:-translate-y-0.5 hover:shadow-lg">
              <div className="event-card-art" aria-hidden="true">
                <img src="/brand/coca-cola-logo-white.png" alt="" />
                <span>EXPERIENCIA COCA-COLA</span>
              </div>
              <div className="p-5 sm:p-6">
              <div className="mb-3 flex items-start justify-between gap-4">
                <div>
                  <span className="inline-flex min-h-7 items-center rounded-full bg-slate-950 px-3 text-[10px] font-bold uppercase tracking-[0.12em] text-white">{event.type}</span>
                  <h3 className="mt-3 text-xl font-bold leading-snug text-slate-950">{event.name}</h3>
                </div>
                <span className="shrink-0 rounded-full bg-emerald-50 px-3 py-1.5 text-[10px] font-bold uppercase tracking-wide text-emerald-700">{event.status}</span>
              </div>

              <p className="min-h-10 text-sm leading-relaxed text-slate-600">{event.description || 'Sin descripción'}</p>

              <dl className="mt-4 space-y-2 border-t border-slate-100 pt-4 text-sm">
                <div className="flex justify-between gap-4"><dt className="text-slate-500">Fecha</dt><dd className="text-right font-semibold text-slate-800">{formatLocalDate(event.eventDate)}</dd></div>
                <div className="flex justify-between gap-4"><dt className="text-slate-500">Ciudad</dt><dd className="text-right font-semibold text-slate-800">{event.location || 'Sin datos'}</dd></div>
                <div className="flex justify-between gap-4"><dt className="text-slate-500">Aforo estimado</dt><dd className="text-right font-semibold text-slate-800">{event.expectedParticipants ?? 'Sin datos'}</dd></div>
              </dl>

              {(role === 'ORGANIZADOR' || isAdministrator) ? <button
                type="button"
                onClick={() => {
                  setRegistrationEvent(event);
                  setRegistration(null);
                  setRegistrationError(null);
                  setIsQrVisible(true);
                }}
                className="coke-button mt-5 w-full"
              >
                Registrar participante
              </button> : null}
              {isAdministrator ? (
                <button
                  type="button"
                  className="coke-button mt-5 w-full"
                  onClick={() => {
                    setEditingEvent(event);
                    setEventSaveError(null);
                    setEventSaveSuccess(null);
                    setIsEventFormOpen(true);
                  }}
                >
                  Editar evento
                </button>
              ) : null}

              {isAdministrator && <PublicEventAccess eventId={event.id} eventStatus={event.status} onChanged={() => loadEvents(true)} />}
              {registrationEvent?.id === event.id && !registration ? (
                <form className="mt-5 space-y-3 rounded-2xl border border-red-100 bg-red-50/70 p-4" noValidate onSubmit={handleRegistration}>
                  <h4 className="font-semibold text-slate-900">Registro para {event.name}</h4>
                  <label className="block text-sm text-slate-700">
                    Nombre
                    <input name="firstName" required maxLength={100} className="coke-input mt-1 w-full" />
                  </label>
                  <label className="block text-sm text-slate-700">
                    Apellido
                    <input name="lastName" required maxLength={100} className="coke-input mt-1 w-full" />
                  </label>
                  <label className="block text-sm text-slate-700">
                    Correo electrónico
                    <input name="email" type="email" inputMode="email" autoComplete="email" required maxLength={255} className="coke-input mt-1 w-full" />
                  </label>
                  <label className="block text-sm text-slate-700">
                    Teléfono (opcional)
                    <input name="phone" type="tel" className="coke-input mt-1 w-full" />
                  </label>
                  {registrationError ? <p role="alert" className="text-sm text-red-700">{registrationError}</p> : null}
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="coke-button w-full"
                  >
                    {isSubmitting ? 'Registrando…' : 'Confirmar registro'}
                  </button>
                </form>
              ) : null}

              {registrationEvent?.id === event.id && registration ? (
                <section className="mt-5 border-t border-slate-200 pt-5" aria-label="Registro confirmado">
                  <h4 className="font-bold text-emerald-700">Registro confirmado</h4>
                  <p className="mt-2 text-sm text-slate-800">{registration.participantName}</p>
                  <p className="text-sm text-slate-600">{registration.eventName}</p>
                  <p className="mt-2 text-sm font-medium text-slate-700">Estado: Inscripción confirmada</p>
                  <button
                    type="button"
                    className="mt-3 rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-800"
                    onClick={() => setIsQrVisible((visible) => !visible)}
                  >
                    {isQrVisible ? 'Ocultar QR' : 'Volver a mostrar QR'}
                  </button>
                  {isQrVisible ? (
                    <>
                      <div className="my-4 flex justify-center rounded-xl bg-white p-3">
                        <QRCodeSVG value={registration.qrToken} title={`QR de registro para ${registration.participantName}`} />
                      </div>
                      <p className="break-all text-xs text-slate-500">Token QR: {registration.qrToken}</p>
                    </>
                  ) : null}
                </section>
              ) : null}
              </div>
            </article>
          )) : null}
      </div>
    </div>
  );
}
