import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { api } from '../services/api';
vi.mock('../services/api', () => ({ api: { get: vi.fn() }, apiError: () => 'Error' }));
import { EventsPage } from './EventsPage';
import { getEvents } from '../services/dashboardService';
import { RoleProvider } from '../components/layout/RoleContext';
import { createEvent, updateEvent } from '../services/eventManagementService';
import { registerParticipant } from '../services/registrationService';

vi.mock('../services/dashboardService', () => ({
  getEvents: vi.fn(),
}));

vi.mock('../services/registrationService', () => ({
  ApiRequestError: class ApiRequestError extends Error {},
  registerParticipant: vi.fn(),
}));

vi.mock('../services/eventManagementService', () => ({
  createEvent: vi.fn(),
  updateEvent: vi.fn(),
}));

const mockedGetEvents = vi.mocked(getEvents);
const mockedRegisterParticipant = vi.mocked(registerParticipant);
const mockedCreateEvent = vi.mocked(createEvent);
const mockedUpdateEvent = vi.mocked(updateEvent);

describe('EventsPage registration QR', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    mockedGetEvents.mockResolvedValue([{
      id: 1,
      name: 'Evento real',
      type: 'ACTIVATION',
      eventDate: '2026-10-03',
      location: 'La Paz',
      status: 'PLANNED',
    }]);
  });

  function renderAsRole(role: 'ADMINISTRADOR' | 'ORGANIZADOR' | 'MARKETING', path: string) {
    vi.mocked(api.get).mockResolvedValue({ data: { id: 1, name: 'Ana', email: 'ana@example.com', activeRole: role, roles: [role], permissions: [], eventIds: [1] } });
    return render(
      <MemoryRouter initialEntries={[path]}>
        <RoleProvider>
          <EventsPage />
        </RoleProvider>
      </MemoryRouter>,
    );
  }

  it('muestra el QR del token devuelto por el backend y permite volver a abrirlo', async () => {
    mockedRegisterParticipant.mockResolvedValue({
      registrationId: 50,
      eventId: 1,
      eventName: 'Evento real',
      participantId: 12,
      participantName: 'Ana Pérez',
      qrToken: 'a84921ab-b4c6-4ea0-b1a2-5423199d9c31',
    });

    renderAsRole('ORGANIZADOR', '/registration');
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar participante' }));
    await userEvent.type(screen.getByLabelText('Nombre'), 'Ana');
    await userEvent.type(screen.getByLabelText('Apellido'), 'Pérez');
    await userEvent.type(screen.getByLabelText('Correo electrónico'), 'ana@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar registro' }));

    expect(await screen.findByText('Registro confirmado')).toBeInTheDocument();
    expect(screen.getByTitle('QR de registro para Ana Pérez')).toBeInTheDocument();
    expect(screen.getByText('Token QR: a84921ab-b4c6-4ea0-b1a2-5423199d9c31')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar QR' }));
    expect(screen.queryByTitle('QR de registro para Ana Pérez')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Volver a mostrar QR' }));
    expect(screen.getByTitle('QR de registro para Ana Pérez')).toBeInTheDocument();
    expect(mockedRegisterParticipant).toHaveBeenCalledWith({
      eventId: 1,
      firstName: 'Ana',
      lastName: 'Pérez',
      email: 'ana@example.com',
      phone: '',
    });
  });

  it('permite al administrador crear un evento mediante la API existente', async () => {
      mockedCreateEvent.mockResolvedValue({
        id: 2,
        name: 'Evento nuevo',
        type: 'ACTIVATION',
        eventDate: '2026-10-03',
        location: 'La Paz',
        status: 'PLANNED',
      });

      renderAsRole('ADMINISTRADOR', '/events');
      await userEvent.click(await screen.findByRole('button', { name: 'Crear evento' }));
      await userEvent.type(screen.getByLabelText('Nombre'), 'Evento nuevo');
      await userEvent.type(screen.getByLabelText('Tipo'), 'ACTIVATION');
      await userEvent.type(screen.getByLabelText('Fecha'), '2026-10-03');
      await userEvent.type(screen.getByLabelText('Ubicación'), 'La Paz');
      const createButtons = screen.getAllByRole('button', { name: 'Crear evento' });
      await userEvent.click(createButtons[createButtons.length - 1]);

      expect(await screen.findByText('Evento creado correctamente.')).toBeInTheDocument();
      expect(mockedCreateEvent).toHaveBeenCalledWith(expect.objectContaining({
        name: 'Evento nuevo',
        type: 'ACTIVATION',
        eventDate: '2026-10-03',
        location: 'La Paz',
        responsible: '',
        status: 'PLANNED',
      }));
      expect(mockedUpdateEvent).not.toHaveBeenCalled();
  });
});
