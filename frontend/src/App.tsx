import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { RoleProvider, useRole, type UserRole } from './components/layout/RoleContext';
import { ActivitiesPage } from './pages/ActivitiesPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { CheckInPage } from './pages/CheckInPage';
import { DashboardPage } from './pages/DashboardPage';
import { EventsPage } from './pages/EventsPage';
import { ParticipantsPage } from './pages/ParticipantsPage';
import { PowerBiAccessPage } from './pages/PowerBiAccessPage';
import { LoginPage, ActivationPage } from './pages/LoginPage';
import { UserManagementPage } from './pages/UserManagementPage';
import { PublicRegistrationPage } from './pages/PublicRegistrationPage';
import { PublicEventsPage } from './pages/PublicEventsPage';
import { OperationsPage } from './pages/OperationsPage';
import { SurveysPage } from './pages/SurveysPage';

type RoleRouteDefinition = {
  path: string;
  title: string;
  allowedRoles: UserRole[];
  page: ReactNode;
};

const allRoles: UserRole[] = ['ADMINISTRADOR', 'ORGANIZADOR', 'MARKETING'];
const adminAndMarketing: UserRole[] = ['ADMINISTRADOR', 'MARKETING'];
const organizerOnly: UserRole[] = ['ADMINISTRADOR', 'ORGANIZADOR'];
const adminOnly: UserRole[] = ['ADMINISTRADOR'];

const roleRoutes: RoleRouteDefinition[] = [
  { path: '/team', title: 'Equipo', allowedRoles: adminOnly, page: <UserManagementPage /> },
  { path: '/operations', title: 'Operación', allowedRoles: organizerOnly, page: <OperationsPage /> },
  { path: '/dashboard', title: 'Dashboard', allowedRoles: allRoles, page: <DashboardPage /> },
  { path: '/events', title: 'Eventos', allowedRoles: allRoles, page: <EventsPage /> },
  { path: '/registration', title: 'Registro', allowedRoles: organizerOnly, page: <EventsPage /> },
  { path: '/participants', title: 'Participantes', allowedRoles: allRoles, page: <ParticipantsPage /> },
  { path: '/check-in', title: 'Check-in', allowedRoles: organizerOnly, page: <CheckInPage /> },
  { path: '/activities', title: 'Actividades', allowedRoles: organizerOnly, page: <ActivitiesPage /> },
  {
    path: '/surveys',
    title: 'Encuestas',
    allowedRoles: organizerOnly,
    page: <SurveysPage />,
  },
  { path: '/analytics', title: 'Reportes', allowedRoles: adminAndMarketing, page: <AnalyticsPage /> },
  { path: '/reports', title: 'Reportes', allowedRoles: adminAndMarketing, page: <AnalyticsPage /> },
  { path: '/insights', title: 'Reportes', allowedRoles: adminAndMarketing, page: <AnalyticsPage /> },
  { path: '/impact-score', title: 'Reportes', allowedRoles: adminAndMarketing, page: <AnalyticsPage /> },
  {
    path: '/power-bi',
    title: 'Power BI',
    allowedRoles: adminAndMarketing,
    page: <PowerBiAccessPage />,
  },
];

function RoleProtectedPage({
  allowedRoles,
  title,
  children,
}: {
  allowedRoles: UserRole[];
  title: string;
  children: ReactNode;
}) {
  const { role, user, loading, connectionError, refresh } = useRole();
  const location = useLocation();

  if (loading) return <main className="p-10" role="status">Conectando con tu equipo…</main>;
  if (connectionError) return <main className="access-layout"><section className="coke-panel p-8"><h1 className="text-2xl font-bold">No pudimos conectar</h1><p className="my-4">Comprueba que el backend esté encendido.</p><button className="coke-button" onClick={() => void refresh()}>Reintentar</button></section></main>;
  if (!user) return <Navigate to="/login" replace />;
  if (!allowedRoles.includes(role)) {
    return <Navigate to="/dashboard" replace state={{ from: location.pathname }} />;
  }

  return <AppShell key={`${user.id}-${role}`} title={title}>{children}</AppShell>;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<PublicEventsPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/activate" element={<ActivationPage />} />
      <Route path="/register/:token" element={<PublicRegistrationPage />} />
      {roleRoutes.map(({ path, title, allowedRoles, page }) => (
        <Route
          key={path}
          path={path}
          element={(
            <RoleProtectedPage allowedRoles={allowedRoles} title={title}>
              {page}
            </RoleProtectedPage>
          )}
        />
      ))}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <RoleProvider>
      <AppRoutes />
    </RoleProvider>
  );
}
