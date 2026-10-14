import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SurveysPage } from './SurveysPage';
import {
  getSurveyEvents,
  getSurveyParticipants,
  submitSurvey,
} from '../services/activitySurveyService';

vi.mock('../services/activitySurveyService', () => ({
  getSurveyEvents: vi.fn(),
  getSurveyParticipants: vi.fn(),
  submitSurvey: vi.fn(),
}));

const mockedGetSurveyEvents = vi.mocked(getSurveyEvents);
const mockedGetSurveyParticipants = vi.mocked(getSurveyParticipants);
const mockedSubmitSurvey = vi.mocked(submitSurvey);

describe('SurveysPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetSurveyEvents.mockResolvedValue([{
      id: 1,
      name: 'Evento real',
      type: 'ACTIVATION',
      eventDate: '2026-10-03',
      location: 'La Paz',
      status: 'ACTIVE',
    }]);
    mockedGetSurveyParticipants.mockResolvedValue([{
      id: 2,
      firstName: 'Ana',
      lastName: 'Pérez',
      email: 'ana@example.com',
      city: '',
      ageRange: '',
      productPreference: '',
      communicationConsent: false,
      registrationSource: '',
    }]);
    mockedSubmitSurvey.mockResolvedValue({
      id: 9,
      eventId: 1,
      participantId: 2,
      overallSatisfaction: 5,
      npsScore: 10,
    });
  });

  it('envía una encuesta válida asociada a evento y participante', async () => {
    render(<SurveysPage />);

    await userEvent.selectOptions(await screen.findByLabelText('Evento'), '1');
    await userEvent.selectOptions(screen.getByLabelText('Participante'), '2');
    await userEvent.type(screen.getByLabelText('Satisfacción (1–5)'), '5');
    await userEvent.type(screen.getByLabelText('NPS (0–10)'), '10');
    await userEvent.type(screen.getByLabelText('Comentario (opcional)'), 'Excelente experiencia');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar encuesta' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Encuesta registrada correctamente (#9).');
    expect(mockedSubmitSurvey).toHaveBeenCalledWith({
      eventId: 1,
      participantId: 2,
      rating: 5,
      nps: 10,
      comment: 'Excelente experiencia',
    });
  });
});
