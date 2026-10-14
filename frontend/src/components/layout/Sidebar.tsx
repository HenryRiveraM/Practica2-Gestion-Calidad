import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';

type SidebarProps = {
  isOpen: boolean;
  onClose: () => void;
};

const navItems = [
  { label: 'Panel', to: '/' },
  { label: 'Eventos', to: '/events' },
  { label: 'Participantes', to: '/participants' },
  { label: 'Asistencia', to: '/check-in' },
  { label: 'Actividades', to: '/activities' },
  { label: 'Productos', to: '/products' },
  { label: 'Analítica', to: '/analytics' },
];

function itemClassName({ isActive }: { isActive: boolean }): string {
  return [
    'flex items-center justify-between rounded-xl px-3 py-2 text-sm font-medium transition',
    isActive ? 'bg-red-600 text-white shadow-soft' : 'text-slate-300 hover:bg-slate-800 hover:text-white',
  ].join(' ');
}

export function Sidebar({ isOpen, onClose }: SidebarProps) {
  const content: ReactNode = (
    <aside className={`fixed inset-y-0 left-0 z-40 w-72 transform border-r border-slate-800 bg-slate-950/95 p-5 backdrop-blur transition duration-200 lg:static lg:translate-x-0 ${isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}`}>
      <div className="mb-8 flex items-center justify-between">
        <div>
          <p className="text-xs uppercase tracking-[0.35em] text-red-400">Coca-Cola</p>
          <h1 className="mt-2 text-2xl font-bold text-white">EventIQ</h1>
        </div>
        <button type="button" className="rounded-lg border border-slate-700 px-2 py-1 text-sm text-slate-300 lg:hidden" onClick={onClose}>
          ✕
        </button>
      </div>

      <nav className="space-y-2">
        {navItems.map(({ label, to }) => (
          <NavLink key={label} to={to} end={to === '/'} className={itemClassName} onClick={onClose}>
            <span>{label}</span>
            <span className="rounded-full bg-white/10 px-2 py-0.5 text-[10px] uppercase tracking-[0.2em] text-red-200">
              {label.slice(0, 1)}
            </span>
          </NavLink>
        ))}
      </nav>

    </aside>
  );

  return content;
}
