import type { Activity, EventItem, Participant } from '../types';
import { api } from './api';
import { toApiRequestError } from './registrationService';

export type ActivityRequest = {
  eventId: number;
  participantId?: number;
  type: string;
  quantity: number;
  result: string;
  occurredAt: string;
};

export type SurveyRequest = {
  eventId: number;
  participantId: number;
  rating: number;
  nps: number;
  comment?: string;
};

export type SurveyResponse = {
  id: number;
  eventId: number;
  participantId: number;
  overallSatisfaction: number;
  npsScore: number;
  comment?: string;
};

async function getResource<T>(path: string): Promise<T> {
  try {
    const { data } = await api.get<T>(path);
    return data;
  } catch (error) {
    throw toApiRequestError(error);
  }
}

export const getActivityEvents = () => getResource<EventItem[]>('/events');
export const getActivityParticipants = () => getResource<Participant[]>('/participants');
export const getActivities = () => getResource<Activity[]>('/activities');
export const getSurveyEvents = () => getResource<EventItem[]>('/events');
export const getSurveyParticipants = () => getResource<Participant[]>('/participants');

export async function createActivity(request: ActivityRequest): Promise<Activity> {
  try {
    const { data } = await api.post<Activity>('/activities', request);
    return data;
  } catch (error) {
    throw toApiRequestError(error);
  }
}

export async function submitSurvey(request: SurveyRequest): Promise<SurveyResponse> {
  try {
    const { data } = await api.post<SurveyResponse>('/surveys', request);
    return data;
  } catch (error) {
    throw toApiRequestError(error);
  }
}
