import axios from 'axios';

const baseURL = import.meta.env.VITE_API_URL || '/api';
const csrfApi = axios.create({ baseURL, withCredentials: true, timeout: 10000 });
export const api = axios.create({ baseURL, withCredentials: true, timeout: 10000 });
api.interceptors.request.use(async (config) => {
  if (!['get', 'head', 'options'].includes(config.method?.toLowerCase() ?? 'get')) {
    const { data } = await csrfApi.get<{ token: string; headerName: string }>('/auth/csrf');
    config.headers.set(data.headerName, data.token);
  }
  return config;
});
api.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401 && !error.config?.url?.startsWith('/auth/')) {
    window.dispatchEvent(new Event('session-expired'));
  }
  return Promise.reject(error);
});
export function apiError(error: unknown): string {
  return axios.isAxiosError(error) ? error.response?.data?.message || 'No se pudo conectar. Intenta nuevamente.' : 'No se pudo completar la operación.';
}
