import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AnalyticsPage } from './AnalyticsPage';
import {
  downloadEventReport,
  getCampaigns,
  getEvents,
  getEventImpactScore,
  getEventInsights,
  getOverview,
} from '../services/dashboardService';

vi.mock('../services/dashboardService', () => ({
  downloadEventReport: vi.fn(),
  getCampaigns: vi.fn(),
  getEvents: vi.fn(),
  getEventImpactScore: vi.fn(),
  getEventInsights: vi.fn(),
  getOverview: vi.fn(),
}));

const mockedDownloadEventReport = vi.mocked(downloadEventReport);
const mockedGetCampaigns = vi.mocked(getCampaigns);
const mockedGetEvents = vi.mocked(getEvents);
const mockedGetEventImpactScore = vi.mocked(getEventImpactScore);
const mockedGetEventInsights = vi.mocked(getEventInsights);
const mockedGetOverview = vi.mocked(getOverview);

describe('AnalyticsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedDownloadEventReport.mockResolvedValue(new Blob());
    mockedGetCampaigns.mockResolvedValue([]);
    mockedGetEvents.mockResolvedValue([{
      id: 1,
      name: 'Evento real',
      type: 'ACTIVATION',
      eventDate: '2026-10-03',
      location: 'La Paz',
      status: 'ACTIVE',
    }]);
    mockedGetEventInsights.mockResolvedValue({
      eventId: 1,
      generatedByAI: false,
      provider: 'gemini',
      model: 'gemini-2.5-flash-lite',
      status: 'NOT_CONFIGURED',
      message: 'Proveedor no configurado',
      summary: null,
      insights: [],
      anomalies: [],
      recommendations: [],
    });
    mockedGetOverview.mockResolvedValue({
      registered: 0,
      attendees: 0,
      attendance_rate: null,
      conversions: 0,
      conversion_rate: null,
      new_participants: 0,
      recurrent_participants: 0,
      nps: null,
      average_rating: null,
      peakAttendanceHours: [],
    });
    mockedGetEventImpactScore.mockResolvedValue({
      eventId: 1,
      score: null,
      status: 'INSUFFICIENT_DATA',
      metrics: {
        attendance: null,
        satisfaction: null,
        conversion: null,
        recurrence: null,
      },
      weights: {
        attendance: 0.3,
        engagement: 0.2,
        satisfaction: 0.2,
        conversion: 0.2,
        recurrence: 0.1,
      },
      appliedWeights: {},
      missingMetrics: ['attendance', 'engagement', 'satisfaction', 'conversion', 'recurrence'],
      formula: 'Promedio ponderado de métricas disponibles.',
      metricDefinitions: {
        attendance: 'Asistentes / inscritos.',
        engagement: 'Participantes con interacción.',
        satisfaction: 'Calificación promedio.',
        conversion: 'Conversiones / asistentes.',
        recurrence: 'Participantes recurrentes.',
      },
    });
  });

  it('permanece visible cuando el evento seleccionado no tiene una métrica en la respuesta', async () => {
    render(<AnalyticsPage />);

    fireEvent.change(await screen.findByRole('combobox', { name: 'Evento' }), {
      target: { value: '1' },
    });

    expect(await screen.findByText('Información insuficiente')).toBeInTheDocument();
    expect(screen.getByText('Impact Score del evento')).toBeInTheDocument();
    expect(screen.getAllByText('Sin datos').length).toBeGreaterThan(0);
    expect(screen.getByRole('region', { name: 'Indicadores' })).toBeInTheDocument();
    expect(mockedGetEventImpactScore).toHaveBeenCalledWith(1);
    expect(mockedGetOverview).toHaveBeenLastCalledWith({ eventId: 1 });
  });
});
