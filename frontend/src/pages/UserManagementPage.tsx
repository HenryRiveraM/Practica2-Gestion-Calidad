import { useEffect, useState, type FormEvent } from 'react';
import { PageIntro } from '../components/common/PageIntro';
import { ROLE_LABELS, type UserRole, useRole } from '../components/layout/RoleContext';
import { api, apiError } from '../services/api';
import { getEvents } from '../services/dashboardService';
import type { EventItem } from '../types';

type TeamUser = { id: number; name: string; email: string; roles: UserRole[]; eventIds: number[]; enabled: boolean; activated: boolean };
const roles = Object.keys(ROLE_LABELS) as UserRole[];
const descriptions: Record<UserRole,string> = { ADMINISTRADOR: 'Gestiona usuarios, eventos y todos los resultados.', ORGANIZADOR: 'Opera únicamente los eventos que le asignes.', MARKETING: 'Consulta reportes y contactos con consentimiento.' };
export function UserManagementPage() {
  const { user: currentUser, refresh } = useRole();
  const [users,setUsers]=useState<TeamUser[]>([]); const [events,setEvents]=useState<EventItem[]>([]);
  const [loading,setLoading]=useState(true); const [error,setError]=useState(''); const [busy,setBusy]=useState(false);
  const [editing,setEditing]=useState<TeamUser|null>(null); const [open,setOpen]=useState(false);
  const [selectedRoles,setSelectedRoles]=useState<UserRole[]>(['ORGANIZADOR']); const [selectedEvents,setSelectedEvents]=useState<number[]>([]);
  const [activationLink,setActivationLink]=useState(''); const [copied,setCopied]=useState(false);
  async function load() {
    setLoading(true); setError('');
    try { const [accounts,eventList]=await Promise.all([api.get<TeamUser[]>('/admin/users'),getEvents()]);setUsers(accounts.data);setEvents(eventList); }
    catch(error) { setError(apiError(error)); } finally { setLoading(false); }
  }
  useEffect(()=>{ void load(); },[]);
  function edit(user: TeamUser|null) { setEditing(user);setSelectedRoles(user?.roles??['ORGANIZADOR']);setSelectedEvents(user?.eventIds??[]);setOpen(true);setError('');setActivationLink(''); }
  function showLink(token:string) { setActivationLink(`${window.location.origin}/activate?token=${encodeURIComponent(token)}`);setCopied(false); }
  async function save(event:FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form=new FormData(event.currentTarget);setBusy(true);setError('');
    const body={name:form.get('name'),roles:selectedRoles,eventIds:selectedRoles.includes('ORGANIZADOR')?selectedEvents:[],enabled:form.get('enabled')==='on'};
    try {
      if(editing) await api.put(`/admin/users/${editing.id}`,body);
      else { const {data}=await api.post('/admin/users',{...body,email:form.get('email')});showLink(data.activationToken); }
      setOpen(false);await load();
      if(editing?.id===currentUser?.id) await refresh();
    } catch(error) {setError(apiError(error));} finally {setBusy(false);}
  }
  async function invite(user:TeamUser) {
    setBusy(true);setError('');try { const {data}=await api.post(`/admin/users/${user.id}/activation`);showLink(data.activationToken); }catch(error){setError(apiError(error));}finally{setBusy(false);}
  }
  return <div className="space-y-6"><PageIntro eyebrow="Administración" title="Un gran equipo, bien conectado" description="Asigna funciones y define quién puede operar cada evento." actions={<button className="coke-button" onClick={()=>edit(null)}>Invitar persona +</button>} />
    <div className="grid gap-4 md:grid-cols-3">{roles.map(role=><section className="coke-panel p-5" key={role}><h2 className="font-bold">{ROLE_LABELS[role]}</h2><p className="mt-2 text-sm text-slate-500">{descriptions[role]}</p></section>)}</div>
    {activationLink && <section className="coke-panel border-red-200 p-6" aria-label="Invitación creada"><h2 className="text-lg font-bold">Invitación lista para compartir</h2><p className="my-2 text-sm text-slate-600">Comparte este enlace de forma privada con la persona. Vence en 24 horas y solo se puede usar una vez.</p><label className="sr-only" htmlFor="invite-link">Enlace de activación</label><input id="invite-link" className="coke-input w-full" readOnly value={activationLink} onFocus={e=>e.currentTarget.select()} /><div className="mt-3 flex gap-3"><button className="coke-button" onClick={()=>{void navigator.clipboard.writeText(activationLink).then(()=>setCopied(true)).catch(()=>setError('Selecciona y copia el enlace manualmente.'));}}>{copied?'Copiado':'Copiar enlace'}</button><button className="coke-button coke-button-secondary" onClick={()=>setActivationLink('')}>Cerrar</button></div></section>}
    {open && <form key={editing?.id??'new'} onSubmit={save} className="coke-panel space-y-5 p-6"><h2 className="text-xl font-bold">{editing?'Editar acceso':'Invitar a tu equipo'}</h2><div className="grid gap-4 sm:grid-cols-2"><label className="access-field">Nombre<input name="name" required maxLength={120} defaultValue={editing?.name} className="coke-input" /></label><label className="access-field">Correo<input name="email" type="email" required maxLength={255} readOnly={!!editing} defaultValue={editing?.email} className="coke-input" /></label></div>
      <fieldset><legend className="mb-3 font-bold">Funciones asignadas</legend><div className="flex flex-wrap gap-4">{roles.map(role=><label key={role} className="flex items-center gap-2"><input type="checkbox" checked={selectedRoles.includes(role)} onChange={e=>setSelectedRoles(e.target.checked?[...selectedRoles,role]:selectedRoles.filter(value=>value!==role))} />{ROLE_LABELS[role]}</label>)}</div></fieldset>
      {selectedRoles.includes('ORGANIZADOR') && <fieldset><legend className="font-bold">Eventos que puede operar</legend><p className="my-2 text-sm text-slate-500">Sin eventos asignados, la persona podrá iniciar sesión, pero no operar eventos.</p><div className="max-h-60 space-y-3 overflow-auto rounded-xl border p-4">{events.map(event=><label key={event.id} className="flex gap-3"><input type="checkbox" checked={selectedEvents.includes(event.id)} onChange={e=>setSelectedEvents(e.target.checked?[...selectedEvents,event.id]:selectedEvents.filter(id=>id!==event.id))} /><span>{event.name}</span></label>)}{events.length===0&&<p>Crea primero un evento.</p>}</div></fieldset>}
      {editing && <label className="flex items-center gap-2"><input name="enabled" type="checkbox" defaultChecked={editing.enabled} />Cuenta habilitada</label>}
      <p className="text-sm text-slate-500">Los cambios de acceso cierran las sesiones de esta cuenta para aplicar sus nuevos permisos.</p><div className="flex gap-3"><button disabled={busy||!selectedRoles.length} className="coke-button">{busy?'Guardando…':editing?'Guardar acceso':'Crear invitación'}</button><button type="button" className="coke-button coke-button-secondary" onClick={()=>setOpen(false)}>Cancelar</button></div>
    </form>}
    {error&&<p className="coke-panel p-4 text-red-700" role="alert">{error}<button className="ml-3 underline" onClick={()=>void load()}>Recargar</button></p>}
    {loading?<p role="status">Cargando equipo…</p>:<div className="coke-table-wrap"><table className="text-left"><thead><tr><th>Persona</th><th>Funciones</th><th>Eventos asignados</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>{users.map(user=><tr key={user.id}><td><strong>{user.name}</strong><small className="block text-slate-500">{user.email}</small></td><td>{user.roles.map(role=>ROLE_LABELS[role]).join(', ')}</td><td>{user.roles.includes('ORGANIZADOR')?user.eventIds.length:'—'}</td><td>{!user.enabled?'Deshabilitada':user.activated?'Activa':'Por activar'}</td><td><div className="flex flex-wrap gap-2"><button className="font-bold text-red-700" onClick={()=>edit(user)}>Editar</button>{!user.activated&&user.enabled&&<button disabled={busy} className="font-bold text-slate-700" onClick={()=>void invite(user)}>Nuevo enlace</button>}</div></td></tr>)}</tbody></table></div>}
  </div>;
}
