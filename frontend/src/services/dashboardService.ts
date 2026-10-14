import type { AnalyticsOverview, Campaign, DashboardSummary, EventImpactScore, EventInsight, EventItem, EventPerformance, Participant, Product, ProductAnalysis } from '../types';
import { api } from './api';

export type AnalyticsFilters = {
  eventId?: number;
  campaignId?: number;
  startDate?: string;
  endDate?: string;
};

export async function getOverview(filters: AnalyticsFilters = {}): Promise<AnalyticsOverview> {
  const { data } = await api.get<AnalyticsOverview>('/analytics/overview', { params: filters });
  return data;
}

export async function getEventImpactScore(eventId: number): Promise<EventImpactScore> {
  const { data } = await api.get<EventImpactScore>(`/analytics/events/${eventId}/impact-score`);
  return data;
}

export async function getEventInsights(eventId: number): Promise<EventInsight> {
  const { data } = await api.get<EventInsight>(`/events/${eventId}/insights`);
  return data;
}

export async function downloadEventReport(eventId: number): Promise<Blob> {
  const { data } = await api.get<Blob>(`/reports/events/${eventId}`, { responseType: 'blob' });
  return data;
}

export async function getEventPerformance(): Promise<EventPerformance[]> {
  const { data } = await api.get<EventPerformance[]>('/analytics/events');
  return data;
}

export async function getProductRanking(): Promise<ProductAnalysis[]> {
  const { data } = await api.get<ProductAnalysis[]>('/analytics/products');
  return data;
}

export async function getDashboardData(): Promise<DashboardSummary> {
  const [overview, events, productRanking, allEvents] = await Promise.all([
    getOverview(),
    getEventPerformance(),
    getProductRanking(),
    getEvents(),
  ]);

  return {
    overview,
    events,
    productRanking,
    recentEvents: allEvents
      .sort((left, right) => right.eventDate.localeCompare(left.eventDate))
      .slice(0, 4),
  };
}

export async function getEvents(): Promise<EventItem[]> {
  const { data } = await api.get<EventItem[]>('/events');
  return data;
}

export async function getParticipants(): Promise<Participant[]> {
  const { data } = await api.get<Participant[]>('/participants');
  return data;
}

export async function getProducts(): Promise<Product[]> {
  const { data } = await api.get<Product[]>('/products');
  return data;
}

export async function getCampaigns(): Promise<Campaign[]> {
  const { data } = await api.get<Campaign[]>('/campaigns');
  return data;
}
