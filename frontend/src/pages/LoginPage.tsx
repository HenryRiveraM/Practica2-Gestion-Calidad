import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { api, apiError } from '../services/api';
import { useRole } from '../components/layout/RoleContext';

export function AccessLayout({ children }: { children: ReactNode }) {
  return <main className="access-layout"><section className="access-story"><img src="/brand/coca-cola-logo-white.png" alt="Coca-Cola" /><p className="text-sm font-bold uppercase tracking-[.2em]">Event Intelligence</p><h1>Grandes experiencias.<br />Un equipo conectado.</h1><p>Organiza, comparte y descubre el impacto de cada encuentro.</p></section><section className="access-form coke-panel">{children}</section></main>;
}
export function LoginPage() {
  const { user, login } = useRole();
  const [setup, setSetup] = useState<boolean | null>(null);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  function loadStatus() { setError(''); void api.get('/auth/status').then(({ data }) => setSetup(data.setupRequired)).catch(error => setError(apiError(error))); }
  useEffect(loadStatus, []);
  if (user) return <Navigate to="/dashboard" replace />;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    const email=String(form.get('email')).trim(); const password=String(form.get('password'));
    setBusy(true); setError('');
    try {
      if (setup) { await api.post('/auth/setup', { code: String(form.get('code')).trim(), name: form.get('name'), email, password }); setSetup(false); }
      await login(email, password);
    } catch(error) { setError(apiError(error)); } finally { setBusy(false); }
  }
  return <AccessLayout><Link to="/" className="mb-6 inline-block text-sm font-semibold text-red-700">← Volver a experiencias</Link><p className="access-eyebrow">Acceso al equipo</p><h2>{setup ? 'Configura tu equipo' : 'Bienvenido de nuevo'}</h2><p className="text-slate-500">{setup ? 'Crea la primera cuenta de administrador con el código temporal de la terminal del backend.' : 'Inicia sesión con la cuenta que te asignó tu administrador.'}</p>
    {setup !== null && <form onSubmit={submit} className="mt-7 space-y-4">
      {setup && <><label className="access-field">Código de configuración<input name="code" required autoComplete="off" className="coke-input" maxLength={64} /></label><label className="access-field">Tu nombre<input name="name" required maxLength={120} autoComplete="name" className="coke-input" /></label></>}
      <label className="access-field">Correo electrónico<input name="email" type="email" required maxLength={255} autoComplete="username" className="coke-input" /></label>
      <label className="access-field">Contraseña<input name="password" type="password" required minLength={setup ? 12 : undefined} maxLength={72} autoComplete={setup ? 'new-password' : 'current-password'} className="coke-input" /></label>
      {setup && <p className="text-xs text-slate-500">Usa al menos 12 caracteres.</p>}
      <button disabled={busy} className="coke-button w-full">{busy ? 'Un momento…' : setup ? 'Crear mi cuenta' : 'Iniciar sesión'}</button>
    </form>}
    {error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}
    {setup === null && <button className="coke-button mt-4" onClick={loadStatus}>Reintentar conexión</button>}
    {!setup && <p className="mt-6 text-sm text-slate-500">¿Primera vez aquí? Abre el enlace de activación que te compartió tu administrador.</p>}
  </AccessLayout>;
}
export function ActivationPage() {
  const [params] = useSearchParams(); const [token] = useState(() => params.get('token') ?? '');
  const [done,setDone]=useState(false); const [busy,setBusy]=useState(false); const [error,setError]=useState('');
  useEffect(() => { window.history.replaceState(null, '', window.location.pathname); }, []);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form=new FormData(event.currentTarget);
    if(form.get('password')!==form.get('confirmation')) { setError('Las contraseñas deben coincidir.'); return; }
    setBusy(true); setError('');
    try { await api.post('/auth/activate', { token, password: form.get('password') }); setDone(true); }
    catch(error) { setError(apiError(error)); } finally { setBusy(false); }
  }
  return <AccessLayout><p className="access-eyebrow">Tu equipo te espera</p><h2>{done ? 'Tu cuenta está lista' : 'Activa tu cuenta'}</h2>
    {done ? <Link className="coke-button mt-6" to="/login">Ir al inicio de sesión</Link> : token ? <form onSubmit={submit} className="mt-6 space-y-4"><p className="text-slate-500">Elige tu contraseña. El enlace es válido durante 24 horas y se usa una sola vez.</p><label className="access-field">Nueva contraseña<input type="password" name="password" required minLength={12} maxLength={72} autoComplete="new-password" className="coke-input" /></label><label className="access-field">Repetir contraseña<input type="password" name="confirmation" required minLength={12} maxLength={72} autoComplete="new-password" className="coke-input" /></label><p className="text-xs text-slate-500">Al menos 12 caracteres.</p><button disabled={busy} className="coke-button w-full">{busy ? 'Activando…' : 'Activar mi cuenta'}</button></form> : <p className="mt-4">Solicita un enlace de activación a tu administrador.</p>}
    {error && <p role="alert" className="mt-4 text-red-700">{error}</p>}
  </AccessLayout>;
}
