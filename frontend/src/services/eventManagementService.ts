import type { EventItem } from '../types';
import { api } from './api';
import { toApiRequestError } from './registrationService';

export type EventWriteRequest = Omit<EventItem, 'id'>;

export async function createEvent(request: EventWriteRequest): Promise<EventItem> {
  try {
    const { data } = await api.post<EventItem>('/events', request);
    return data;
  } catch (error) {
    throw toApiRequestError(error);
  }
}

export async function updateEvent(id: number, request: EventWriteRequest): Promise<EventItem> {
  try {
    const { data } = await api.put<EventItem>(`/events/${id}`, request);
    return data;
  } catch (error) {
    throw toApiRequestError(error);
  }
}
