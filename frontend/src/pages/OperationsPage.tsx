import { Link } from 'react-router-dom';
import { PageIntro } from '../components/common/PageIntro';
export function OperationsPage() {
  const actions=[['Registro','Inscribe participantes y entrega su QR.','/registration'],['Check-in','Confirma la llegada de cada participante.','/check-in'],['Actividades','Registra degustaciones y participación.','/activities'],['Encuestas','Recoge opiniones después de la experiencia.','/surveys']];
  return <div className="space-y-6"><PageIntro eyebrow="Operación del evento" title="Todo listo para recibirlos" description="Accede a las herramientas del equipo en campo." /><div className="grid gap-5 sm:grid-cols-2">{actions.map(([title,description,to],i)=><Link key={to} to={to} className="coke-panel p-7 transition hover:-translate-y-1"><span className="text-sm font-bold text-red-600">0{i+1}</span><h2 className="mt-4 text-2xl font-bold">{title}</h2><p className="mt-2 text-slate-500">{description}</p><span className="mt-6 block font-semibold text-red-600">Abrir herramienta →</span></Link>)}</div></div>;
}
