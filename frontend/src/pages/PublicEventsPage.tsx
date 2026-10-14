import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { PublicLayout } from '../components/public/PublicLayout';
import { api, apiError } from '../services/api';
import { formatLocalDate } from '../utils/date';

type PublicEvent = { id: number; name: string; type: string; eventDate: string; location: string; description?: string; status: 'PLANNED' | 'ACTIVE'; token: string };
const typeLabels: Record<string,string> = { ACTIVATION:'Activación de marca', TASTING:'Degustación', LAUNCH:'Lanzamiento', FESTIVAL:'Festival', SPONSORSHIP:'Encuentro', PROMOTION:'Promoción', SAMPLING:'Degustación' };
export function PublicEventsPage() {
  const [events,setEvents]=useState<PublicEvent[]>([]);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [query,setQuery]=useState('');
  const [status,setStatus]=useState('ALL');
  const requestVersion = useRef(0);
  const load = useCallback(async (background = false) => {
    const version = ++requestVersion.current;
    if (!background) { setLoading(true); setError(''); }
    try {
      const { data } = await api.get<PublicEvent[]>('/public/events');
      if (version === requestVersion.current) { setEvents(data); setError(''); }
    } catch(error) { if (version === requestVersion.current && !background) setError(apiError(error)); }
    finally { if (version === requestVersion.current) setLoading(false); }
  },[]);
  useEffect(()=>{
    void load();
    const refresh = () => { if (document.visibilityState === 'visible') void load(true); };
    window.addEventListener('focus',refresh);
    document.addEventListener('visibilitychange',refresh);
    const interval=window.setInterval(refresh,15000);
    return () => { ++requestVersion.current; window.removeEventListener('focus',refresh); document.removeEventListener('visibilitychange',refresh); window.clearInterval(interval); };
  },[load]);
  useEffect(()=>{ if (!loading && window.location.hash === '#eventos') document.getElementById('eventos')?.scrollIntoView(); },[loading]);
  const visible = useMemo(()=>events.filter(event => (status==='ALL'||event.status===status) && [event.name,event.location,event.description].some(value=>value?.toLocaleLowerCase('es').includes(query.trim().toLocaleLowerCase('es')))),[events,status,query]);
  return <PublicLayout>
    <section className="experience-hero" aria-labelledby="experience-title">
      <div className="experience-hero-copy"><p className="public-eyebrow">Experiencias Coca-Cola</p><h1 id="experience-title">Hay momentos<br />que se viven<br /><em>mejor juntos.</em></h1><p className="experience-intro">Encuentra tu próxima experiencia, reserva tu lugar y ven a compartir algo especial.</p><a className="public-hero-button" href="#eventos">Encuentra tu evento <span aria-hidden="true">↗</span></a><p className="experience-note">Regístrate online · Guarda tu QR · Disfruta el momento</p></div>
      <div className="experience-hero-art" aria-hidden="true"><div className="experience-orbit" /><span className="experience-sticker">¡Nos vemos<br />ahí!</span><div className="experience-bear"><img src="/brand/polar-bear-arm.png" className="experience-bear-arm" alt="" /><img src="/brand/polar-bear-body.png" alt="" /></div><span className="experience-art-caption">Destapa la felicidad.</span></div>
    </section>
    <div className="experience-ribbon" aria-hidden="true"><span>Comparte una Coca-Cola</span><span>✦</span><span>Siente el sabor</span><span>✦</span><span>Vive la experiencia</span><span>✦</span><span>Comparte una Coca-Cola</span></div>
    <section className="public-events-section" id="eventos" aria-labelledby="events-title">
      <div className="public-section-heading"><div><p className="public-eyebrow">El siguiente momento empieza aquí</p><h2 id="events-title">Nos encontramos en…</h2></div><p>Explora los eventos con inscripción abierta<br className="hidden sm:block" /> y elige el que quieres vivir.</p></div>
      <div className="public-event-filters"><div className="public-filter-pills" role="group" aria-label="Filtrar eventos por estado">{[['ALL','Todos'],['ACTIVE','Activos'],['PLANNED','Planificados']].map(([value,label])=><button key={value} aria-pressed={status===value} onClick={()=>setStatus(value)}>{label}</button>)}</div><label className="public-event-search"><span className="sr-only">Buscar por evento o lugar</span><input type="search" value={query} onChange={e=>setQuery(e.target.value)} placeholder="Busca un evento o lugar" /></label></div>
      {loading ? <div className="public-empty" role="status">Estamos preparando las experiencias…</div> : error ? <div className="public-empty" role="alert"><h3>No pudimos cargar los eventos</h3><p>{error}</p><button className="coke-button" onClick={()=>void load()}>Reintentar</button></div> : <>
        <p className="public-result-count" aria-live="polite">{visible.length} {visible.length===1?'experiencia disponible':'experiencias disponibles'}</p>
        {visible.length===0 ? <div className="public-empty"><h3>{events.length?'Todavía no encontramos ese momento':'Estamos preparando nuevas experiencias'}</h3><p>{events.length?'Prueba con otro nombre o lugar.':'Vuelve pronto para descubrir los próximos eventos.'}</p>{events.length>0&&<button className="coke-button" onClick={()=>{setQuery('');setStatus('ALL');}}>Ver todos los eventos</button>}</div> : <div className="public-event-grid">{visible.map((event,index)=><article className="public-event-card" key={event.id}>
          <div className={`public-event-art public-event-art-${index%3}`} aria-hidden="true"><span className="public-art-circle" /><img src="/brand/coca-cola-logo-white.png" alt="" /><span>{['Comparte.','Disfruta.','Descubre.'][index%3]}</span></div>
          <div className="public-event-body"><div className="public-event-meta"><span>{typeLabels[event.type]??event.type.replaceAll('_',' ')}</span><span className="public-open-badge">Inscripciones abiertas</span></div><h3>{event.name}</h3><p className="public-event-description">{event.description||'Una experiencia para compartir y disfrutar juntos.'}</p><dl><div><dt>Cuándo</dt><dd>{formatLocalDate(event.eventDate)}</dd></div><div><dt>Dónde</dt><dd>{event.location||'Ubicación por confirmar'}</dd></div></dl><Link className="public-register-button" to={`/register/${encodeURIComponent(event.token)}`} aria-label={`Quiero participar en ${event.name}`}>Quiero participar <span aria-hidden="true">↗</span></Link></div>
        </article>)}</div>}
      </>}
    </section>
    <section className="public-steps-section" id="como-participar" aria-labelledby="steps-title"><div><p className="public-eyebrow">Así de fácil</p><h2 id="steps-title">Tu próxima historia,<br />en tres pasos.</h2><p>No necesitas una cuenta para inscribirte.</p></div><ol>{[['Elige tu experiencia','Explora los eventos y encuentra tu próximo plan.'],['Reserva tu lugar','Completa tus datos y recibe tu código QR en pantalla.'],['Ven a disfrutar','Guarda tu QR y preséntalo al equipo cuando llegues.']].map(([title,description],i)=><li key={title}><span>0{i+1}</span><div><h3>{title}</h3><p>{description}</p></div></li>)}</ol></section>
  </PublicLayout>;
}
