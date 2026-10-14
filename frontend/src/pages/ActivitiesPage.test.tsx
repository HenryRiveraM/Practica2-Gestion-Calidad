import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ActivitiesPage } from './ActivitiesPage';
import {
  createActivity,
  getActivities,
  getActivityEvents,
  getActivityParticipants,
} from '../services/activitySurveyService';

vi.mock('../services/activitySurveyService', () => ({
  createActivity: vi.fn(),
  getActivities: vi.fn(),
  getActivityEvents: vi.fn(),
  getActivityParticipants: vi.fn(),
}));

const mockedCreateActivity = vi.mocked(createActivity);
const mockedGetActivities = vi.mocked(getActivities);
const mockedGetActivityEvents = vi.mocked(getActivityEvents);
const mockedGetActivityParticipants = vi.mocked(getActivityParticipants);

describe('ActivitiesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetActivities.mockResolvedValue([]);
    mockedGetActivityEvents.mockResolvedValue([{
      id: 1,
      name: 'Evento real',
      type: 'ACTIVATION',
      eventDate: '2026-10-03',
      location: 'La Paz',
      status: 'ACTIVE',
    }]);
    mockedGetActivityParticipants.mockResolvedValue([]);
    mockedCreateActivity.mockResolvedValue({
      id: 5,
      eventId: 1,
      name: 'SAMPLING',
      type: 'SAMPLING',
      quantity: 12,
      result: 'Se entregaron muestras',
      status: 'COMPLETED',
    });
  });

  it('registra sampling y observación con el endpoint real de actividades', async () => {
    render(<ActivitiesPage />);

    await userEvent.selectOptions(await screen.findByLabelText('Evento'), '1');
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'SAMPLING');
    await userEvent.type(screen.getByLabelText('Cantidad / resultado cuantificable'), '12');
    await userEvent.type(screen.getByLabelText('Fecha y hora'), '2026-10-03T10:00');
    await userEvent.type(screen.getByLabelText('Resultado u observación'), 'Se entregaron muestras');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Actividad, sampling u observación registrada correctamente.',
    );
    expect(mockedCreateActivity).toHaveBeenCalledWith(expect.objectContaining({
      eventId: 1,
      type: 'SAMPLING',
      quantity: 12,
      result: 'Se entregaron muestras',
    }));
    expect(mockedCreateActivity.mock.calls[0][0].occurredAt).toContain('2026-10-03T');
  });
});
