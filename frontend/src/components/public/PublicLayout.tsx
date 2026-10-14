import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useRole } from '../layout/RoleContext';

export function PublicLayout({ children }: { children: ReactNode }) {
  const { user } = useRole();
  return <div className="public-site">
    <a href="#public-content" className="public-skip">Saltar al contenido</a>
    <header className="public-header">
      <Link to="/" aria-label="Coca-Cola experiencias, inicio"><img src="/brand/coca-cola-logo-red.webp" alt="Coca-Cola" width="180" height="59" /></Link>
      <nav aria-label="Navegación de experiencias">
        <a href="/#eventos">Explorar eventos</a>
        <a href="/#como-participar" className="public-how-link">Cómo participar</a>
        <Link to={user ? '/dashboard' : '/login'} className="public-team-link">{user ? 'Mi panel' : 'Acceso del equipo'} <span aria-hidden="true">↗</span></Link>
      </nav>
    </header>
    <main id="public-content">{children}</main>
    <footer className="public-footer"><Link to="/" aria-label="Volver al inicio"><img src="/brand/coca-cola-logo-white.png" alt="Coca-Cola" width="160" height="52" /></Link><p>Momentos que saben mejor juntos.</p><Link to={user ? '/dashboard' : '/login'}>{user ? 'Volver a mi panel' : 'Soy parte del equipo'} <span aria-hidden="true">↗</span></Link></footer>
  </div>;
}
