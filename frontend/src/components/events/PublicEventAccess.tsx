import { useEffect, useState } from 'react';
import { api, apiError } from '../../services/api';
type Access = { enabled: boolean; token?: string; eventStatus: string };
type Props = { eventId: number; eventStatus: string; onChanged: () => Promise<void> };
export function PublicEventAccess({ eventId, eventStatus, onChanged }: Props) {
  const [access,setAccess]=useState<Access|null>(null);
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const [copied,setCopied]=useState(false);
  useEffect(()=>{setAccess(null);setCopied(false);},[eventId,eventStatus]);
  async function load() {
    setBusy(true);setError('');
    try {setAccess((await api.get<Access>(`/admin/events/${eventId}/public-access`)).data);}
    catch(error){setError(apiError(error));}finally{setBusy(false);}
  }
  const closed = ['COMPLETED','CANCELLED'].includes(eventStatus);
  async function toggle() {
    setBusy(true);setError('');
    try {
      await api.put(`/admin/events/${eventId}/public-access`,{enabled:!access?.enabled,reactivateEvent:closed&&!access?.enabled});
      await onChanged();
      setAccess((await api.get<Access>(`/admin/events/${eventId}/public-access`)).data);
    }catch(error){setError(apiError(error));}finally{setBusy(false);}
  }
  const link=access?.enabled&&access.token?`${window.location.origin}/register/${access.token}`:'';
  return <div className="mt-3 border-t border-slate-100 pt-3">
    {access===null?<button className="w-full py-2 text-sm font-bold text-red-700" disabled={busy} onClick={()=>void load()}>Publicación e inscripciones ↗</button>:<div className="space-y-3">
      <p className="text-sm font-bold">{access.enabled?'Publicado en la landing · Inscripciones abiertas':closed?'Evento cerrado · No visible en la landing':'No publicado en la landing'}</p>
      {link&&<><input aria-label="Enlace público de inscripción" readOnly value={link} className="coke-input w-full text-xs" onFocus={e=>e.currentTarget.select()} /><div className="flex flex-wrap gap-4"><button className="text-sm font-bold text-red-700" onClick={()=>{void navigator.clipboard.writeText(link).then(()=>setCopied(true)).catch(()=>setError('Selecciona y copia el enlace manualmente.'));}}>{copied?'Copiado':'Copiar enlace'}</button><a className="text-sm font-bold text-red-700" href="/#eventos" target="_blank" rel="noreferrer">Ver en la landing ↗</a></div></>}
      <p className="text-xs text-slate-500">{closed?'Al reactivar, el estado cambiará a Activo y se abrirán las inscripciones en la landing.':access.enabled?'Al cerrar las inscripciones, el evento dejará de aparecer en la landing.':'Publica el evento para que aparezca en la landing y puedan registrarse.'}</p>
      <button className="coke-button coke-button-secondary w-full" disabled={busy} onClick={()=>void toggle()}>{busy?'Guardando…':access.enabled?'Cerrar inscripciones y ocultar':closed?'Reactivar y publicar':'Publicar y abrir inscripciones'}</button>
    </div>}
    {error&&<p role="alert" className="text-sm text-red-700">{error}</p>}
  </div>;
}
