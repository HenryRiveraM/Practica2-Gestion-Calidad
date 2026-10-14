import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRole } from '../components/layout/RoleContext';
import { LoadError } from '../components/common/LoadError';
import { PageIntro } from '../components/common/PageIntro';
import { getParticipants } from '../services/dashboardService';
import type { Participant } from '../types';

export function ParticipantsPage() {
  const { role } = useRole();
  const [participants, setParticipants] = useState<Participant[]>([]);
  const [query, setQuery] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);

  const loadParticipants = useCallback(async () => {
    setIsLoading(true);
    setError(false);
    try {
      setParticipants(await getParticipants());
    } catch {
      setParticipants([]);
      setError(true);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => { void loadParticipants(); }, [loadParticipants]);

  const filteredParticipants = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('es');
    if (!normalizedQuery) return participants;
    return participants.filter((participant) => [
      participant.firstName,
      participant.lastName,
      participant.email,
      participant.city,
      participant.productPreference,
    ].some((value) => value?.toLocaleLowerCase('es').includes(normalizedQuery)));
  }, [participants, query]);

  function exportParticipants() {
    const rows = [
      ['Nombre', 'Correo', 'Ciudad', 'Preferencia', 'Fuente', 'Consentimiento'],
      ...filteredParticipants.map((participant) => [
        `${participant.firstName} ${participant.lastName}`,
        participant.email,
        participant.city,
        participant.productPreference,
        participant.registrationSource,
        participant.communicationConsent ? 'Activo' : 'Inactivo',
      ]),
    ];
    const csv = rows.map((row) => row.map((value) => `"${String(value ?? '').replace(/^[=+@\-\t\r]/, prefix => `'${prefix}`).replaceAll('"', '""')}"`).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'participantes-coca-cola.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="space-y-6">
      <PageIntro
        eyebrow="Participantes"
        title="Directorio de participantes"
        description={role === 'MARKETING' ? 'Contactos con consentimiento para comunicaciones. Las exportaciones respetan este mismo filtro.' : role === 'ORGANIZADOR' ? 'Participantes de tus eventos asignados.' : 'Consulta los datos de contacto, preferencias y consentimiento de cada persona.'}
        actions={<button type="button" className="coke-button coke-button-secondary" onClick={exportParticipants} disabled={!filteredParticipants.length}>Exportar lista</button>}
      />

      <section className="coke-panel p-4 sm:p-5" aria-label="Buscar participantes">
        <label htmlFor="participant-search" className="sr-only">Buscar participante</label>
        <input
          id="participant-search"
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Buscar por nombre, correo, ciudad o preferencia"
          className="coke-input w-full sm:max-w-md"
        />
        <p className="mt-3 text-sm text-slate-500" aria-live="polite">
          {isLoading ? 'Cargando participantes…' : `${filteredParticipants.length} de ${participants.length} participantes`}
        </p>
      </section>

      {isLoading ? <p role="status" className="coke-panel p-5 text-slate-600">Cargando participantes…</p> : null}
      {!isLoading && error ? <LoadError onRetry={() => void loadParticipants()} /> : null}
      {!isLoading && !error && participants.length === 0 ? <p className="coke-panel p-5 text-slate-600">Todavía no hay participantes registrados.</p> : null}
      {!isLoading && !error && participants.length > 0 && filteredParticipants.length === 0 ? (
        <p className="coke-panel p-5 text-slate-600">No encontramos participantes que coincidan con “{query}”.</p>
      ) : null}
      {!isLoading && !error && filteredParticipants.length > 0 ? (
        <div className="coke-table-wrap" role="region" aria-label="Lista de participantes" tabIndex={0}>
          <table className="text-left">
            <thead>
              <tr>
                <th scope="col">Participante</th>
                <th scope="col">Contacto</th>
                <th scope="col">Ciudad</th>
                <th scope="col">Preferencia</th>
                <th scope="col">Fuente</th>
                <th scope="col">Consentimiento</th>
              </tr>
            </thead>
            <tbody>
              {filteredParticipants.map((participant) => (
                <tr key={participant.id}>
                  <td className="font-semibold text-slate-900">{participant.firstName} {participant.lastName}</td>
                  <td className="text-slate-600">{participant.email}</td>
                  <td className="text-slate-600">{participant.city || '—'}</td>
                  <td className="text-slate-600">{participant.productPreference || '—'}</td>
                  <td className="text-slate-600">{participant.registrationSource || '—'}</td>
                  <td>
                    <span className={`inline-flex rounded-full px-3 py-1 text-sm font-semibold ${participant.communicationConsent ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>
                      {participant.communicationConsent ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}
