import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CheckInPage } from './CheckInPage';
import { ApiRequestError, checkInParticipant } from '../services/registrationService';
import { getOverview } from '../services/dashboardService';

vi.mock('../services/registrationService', async (importOriginal) => {
  const original = await importOriginal<typeof import('../services/registrationService')>();
  return {
    ...original,
    checkInParticipant: vi.fn(),
  };
});

vi.mock('../services/dashboardService', () => ({
  getOverview: vi.fn(),
}));

const mockedCheckIn = vi.mocked(checkInParticipant);
const mockedGetOverview = vi.mocked(getOverview);

describe('CheckInPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetOverview.mockResolvedValue({
      registered: 1,
      attendees: 0,
      attendance_rate: 0,
      conversions: 0,
      new_participants: 1,
      recurrent_participants: 0,
      peakAttendanceHours: [],
    });
  });

  it('muestra confirmación después de un check-in exitoso', async () => {
    mockedGetOverview
      .mockResolvedValueOnce({
        registered: 1,
        attendees: 0,
        attendance_rate: 0,
        conversions: 0,
        new_participants: 1,
        recurrent_participants: 0,
        peakAttendanceHours: [],
      })
      .mockResolvedValueOnce({
        registered: 1,
        attendees: 1,
        attendance_rate: 100,
        conversions: 0,
        new_participants: 1,
        recurrent_participants: 0,
        peakAttendanceHours: [],
      });
    mockedCheckIn.mockResolvedValue({
      attendanceId: 11,
      eventId: 4,
      participantId: 8,
      eventName: 'Evento real',
      participantName: 'Ana Pérez',
      status: 'CHECKED_IN',
      checkInAt: '2026-10-03T13:00:00Z',
    });

    render(<CheckInPage />);
    await userEvent.type(screen.getByLabelText('Token QR'), 'token-de-registro');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar asistencia' }));

    expect(await screen.findByText('Check-in exitoso')).toBeInTheDocument();
    expect(screen.getByText('Participante: Ana Pérez')).toBeInTheDocument();
    expect(screen.getByText('Evento: Evento real')).toBeInTheDocument();
    expect(mockedCheckIn).toHaveBeenCalledWith('token-de-registro');
    expect(await screen.findByText('100.0% de asistencia')).toBeInTheDocument();
    expect(mockedGetOverview).toHaveBeenCalledTimes(2);
  });

  it('muestra errores del backend incluido el check-in duplicado', async () => {
    mockedCheckIn.mockRejectedValue(new ApiRequestError('Esta inscripción ya realizó el check-in', 409));

    render(<CheckInPage />);
    await userEvent.type(screen.getByLabelText('Token QR'), 'token-repetido');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar asistencia' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este QR ya fue utilizado: Esta inscripción ya realizó el check-in',
    );
  });

  it('indica estado de carga mientras espera la respuesta', async () => {
    let resolveCheckIn: (value: {
      attendanceId: number;
      eventId: number;
      participantId: number;
      eventName: string;
      participantName: string;
      status: 'CHECKED_IN';
      checkInAt: string;
    }) => void = () => {};
    mockedCheckIn.mockReturnValue(new Promise((resolve) => {
      resolveCheckIn = resolve;
    }));

    render(<CheckInPage />);
    await userEvent.type(screen.getByLabelText('Token QR'), 'token-pendiente');
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar asistencia' }));

    expect(screen.getByRole('button', { name: 'Validando QR…' })).toBeDisabled();

    await act(async () => {
      resolveCheckIn({
        attendanceId: 11,
        eventId: 4,
        participantId: 8,
        eventName: 'Evento real',
        participantName: 'Ana Pérez',
        status: 'CHECKED_IN',
        checkInAt: '2026-10-03T13:00:00Z',
      });
    });
    await waitFor(() => expect(screen.getByText('Check-in exitoso')).toBeInTheDocument());
  });
});
