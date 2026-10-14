import { api } from './api';

export type RegistrationRequest = {
  eventId: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
};

export type RegistrationResponse = {
  registrationId: number;
  eventId: number;
  eventName: string;
  participantId: number;
  participantName: string;
  qrToken: string;
};

export type CheckInResponse = {
  attendanceId: number;
  eventId: number;
  participantId: number;
  eventName: string;
  participantName: string;
  status: 'CHECKED_IN';
  checkInAt: string;
};

export class ApiRequestError extends Error {
  readonly status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
  }
}

export function toApiRequestError(error: unknown): ApiRequestError {
  if (typeof error === 'object' && error !== null && 'isAxiosError' in error) {
    const response = 'response' in error && typeof error.response === 'object' && error.response !== null
      ? error.response
      : undefined;
    const data = response && 'data' in response && typeof response.data === 'object' && response.data !== null
      ? response.data
      : undefined;
    const message = data && 'message' in data && typeof data.message === 'string'
      ? data.message
      : 'No fue posible completar la solicitud.';
    const status = response && 'status' in response && typeof response.status === 'number'
      ? response.status
      : undefined;
    return new ApiRequestError(message, status);
  }

  return new ApiRequestError('No fue posible completar la solicitud.');
}

export async function registerParticipant(request: RegistrationRequest): Promise<RegistrationResponse> {
  try {
    const { data } = await api.post<RegistrationResponse>('/registrations', request);
    return data;
  } catch (error) {
    throw toApiRequestError(error);
  }
}

export async function checkInParticipant(qrToken: string): Promise<CheckInResponse> {
  try {
    const { data } = await api.post<CheckInResponse>('/attendances/check-in', { qrToken });
    return data;
  } catch (error) {
    throw toApiRequestError(error);
  }
}
