import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import App from './App';
import { api } from './services/api';
import type { UserRole } from './components/layout/RoleContext';
vi.mock('./services/api', () => ({ api: { get: vi.fn(), post: vi.fn() }, apiError: () => 'Error' }));
let activeRole: UserRole = 'ADMINISTRADOR';
const account = () => ({ id: 1, name: 'Ana', email: 'ana@example.com', activeRole, roles: ['ADMINISTRADOR', 'ORGANIZADOR', 'MARKETING'], eventIds: [1], permissions: [] });

vi.mock('./pages/ActivitiesPage', () => ({ ActivitiesPage: () => <div>Actividades mock</div> }));
vi.mock('./pages/AnalyticsPage', () => ({ AnalyticsPage: () => <div>Analytics mock</div> }));
vi.mock('./pages/CheckInPage', () => ({ CheckInPage: () => <div>Check-in mock</div> }));
vi.mock('./pages/DashboardPage', () => ({ DashboardPage: () => <div>Dashboard mock</div> }));
vi.mock('./pages/EventsPage', () => ({ EventsPage: () => <div>Eventos mock</div> }));
vi.mock('./pages/ParticipantsPage', () => ({ ParticipantsPage: () => <div>Participantes mock</div> }));
vi.mock('./pages/PowerBiAccessPage', () => ({ PowerBiAccessPage: () => <div>Power BI mock</div> }));
vi.mock('./pages/SurveysPage', () => ({ SurveysPage: () => <div>Encuestas mock</div> }));

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('navegación por rol', () => {
  beforeEach(async () => {
    activeRole = 'ADMINISTRADOR';
    vi.mocked(api.get).mockImplementation(async () => ({ data: account() }));
    vi.mocked(api.post).mockImplementation(async (_url, body) => {
      activeRole = (body as { role: UserRole }).role;
      return { data: account() };
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('muestra navegación de administrador y permite operar check-in', async () => {
    activeRole = 'ADMINISTRADOR';
    renderAt('/check-in');

    expect(await screen.findByText('Check-in mock')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toHaveTextContent(
      'DashboardEventosParticipantesOperaciónEquipoReportesPower BI',
    );
    expect(screen.queryByRole('link', { name: 'Escanear QR / Check-in' })).not.toBeInTheDocument();
  });

  it('redirige al organizador a Inicio si intenta abrir Analytics directamente', async () => {
    activeRole = 'ORGANIZADOR';
    renderAt('/analytics');

    expect(await screen.findByText('Dashboard mock')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toHaveTextContent(
      'InicioEventosParticipantesRegistroEscanear QR / Check-inActividadesEncuestas',
    );
    expect(screen.queryByText('Analytics mock')).not.toBeInTheDocument();
  });

  it('muestra navegación de marketing y concentra analytics en Reportes', async () => {
    activeRole = 'MARKETING';
    renderAt('/reports');

    expect(await screen.findByText('Analytics mock')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toHaveTextContent(
      'DashboardEventosParticipantesReportesPower BI',
    );
    expect(screen.getByRole('link', { name: 'Power BI' })).toBeInTheDocument();
  });

  it('solicita al servidor cambiar una función asignada y navega al dashboard', async () => {
    activeRole = 'ADMINISTRADOR';
    renderAt('/events');

    fireEvent.click(await screen.findByLabelText('Cuenta de Ana, Administrador'));
    fireEvent.change(screen.getByRole('combobox', { name: 'Cambiar función' }), {
      target: { value: 'ORGANIZADOR' },
    });

    expect(await screen.findByText('Dashboard mock')).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/auth/active-role', { role: 'ORGANIZADOR' });
    expect(screen.getByRole('link', { name: 'Escanear QR / Check-in' })).toHaveAttribute('href', '/check-in');
  });
});
