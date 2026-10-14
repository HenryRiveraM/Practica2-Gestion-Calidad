import { createContext, useContext, useCallback, useEffect, useState, type ReactNode } from 'react';

import axios from 'axios';
import { api } from '../../services/api';

export type UserRole = 'ADMINISTRADOR' | 'ORGANIZADOR' | 'MARKETING';

export type RoleNavigationItem = {
  label: string;
  to: string;
};

export const ROLE_LABELS: Record<UserRole, string> = {
  ADMINISTRADOR: 'Administrador',
  ORGANIZADOR: 'Organizador',
  MARKETING: 'Marketing',
};

export function isUserRole(value: string): value is UserRole {
  return value === 'ADMINISTRADOR' || value === 'ORGANIZADOR' || value === 'MARKETING';
}

export const ROLE_NAVIGATION: Record<UserRole, RoleNavigationItem[]> = {
  ADMINISTRADOR: [
    { label: 'Dashboard', to: '/dashboard' },
    { label: 'Eventos', to: '/events' },
    { label: 'Participantes', to: '/participants' },
    { label: 'Operación', to: '/operations' },
    { label: 'Equipo', to: '/team' },
    { label: 'Reportes', to: '/reports' },
    { label: 'Power BI', to: '/power-bi' },
  ],
  ORGANIZADOR: [
    { label: 'Inicio', to: '/dashboard' },
    { label: 'Eventos', to: '/events' },
    { label: 'Participantes', to: '/participants' },
    { label: 'Registro', to: '/registration' },
    { label: 'Escanear QR / Check-in', to: '/check-in' },
    { label: 'Actividades', to: '/activities' },
    { label: 'Encuestas', to: '/surveys' },
  ],
  MARKETING: [
    { label: 'Dashboard', to: '/dashboard' },
    { label: 'Eventos', to: '/events' },
    { label: 'Participantes', to: '/participants' },
    { label: 'Reportes', to: '/reports' },
    { label: 'Power BI', to: '/power-bi' },
  ],
};

export type SessionUser = {
  id: number; name: string; email: string; roles: UserRole[]; activeRole: UserRole;
  permissions: string[]; eventIds: number[];
};
type RoleContextValue = {
  role: UserRole; user: SessionUser | null; loading: boolean; connectionError: boolean;
  refresh: () => Promise<void>; login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>; setRole: (role: UserRole) => Promise<void>;
};
const RoleContext = createContext<RoleContextValue | null>(null);
export function RoleProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [connectionError, setConnectionError] = useState(false);
  const refresh = useCallback(async () => {
    setLoading(true); setConnectionError(false);
    try { setUser((await api.get<SessionUser>('/auth/me')).data); }
    catch (error) { setUser(null); setConnectionError(!(axios.isAxiosError(error) && error.response?.status === 401)); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => {
    const expired = () => setUser(null);
    window.addEventListener('session-expired', expired);
    return () => window.removeEventListener('session-expired', expired);
  }, []);
  async function login(email: string, password: string) {
    await api.post('/auth/login', new URLSearchParams({ email, password }), { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
    const { data } = await api.get<SessionUser>('/auth/me'); setUser(data); setConnectionError(false);
  }
  async function logout() { await api.post('/auth/logout'); setUser(null); }
  async function setRole(role: UserRole) { setUser((await api.post<SessionUser>('/auth/active-role', { role })).data); }
  return <RoleContext.Provider value={{ user, role: user?.activeRole ?? 'ORGANIZADOR', loading, connectionError, refresh, login, logout, setRole }}>{children}</RoleContext.Provider>;
}
export function useRole(): RoleContextValue {
  const context = useContext(RoleContext);
  if (!context) throw new Error('useRole debe utilizarse dentro de RoleProvider.');
  return context;
}
