import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { isUserRole, ROLE_LABELS, ROLE_NAVIGATION, useRole, type UserRole } from './RoleContext';

type TopBarProps = {
  title: string;
};

function linkClassName({ isActive }: { isActive: boolean }): string {
  return `coke-nav-link${isActive ? ' is-active' : ''}`;
}

export function TopBar({ title }: TopBarProps) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { role, user, setRole, logout } = useRole();
  const navigate = useNavigate();
  const [profileError, setProfileError] = useState('');
  const navItems = ROLE_NAVIGATION[role];

  function closeMenu() {
    setMobileMenuOpen(false);
  }

  async function handleRoleChange(nextRole: UserRole) {
    setProfileError('');
    try { await setRole(nextRole); closeMenu(); navigate('/dashboard'); }
    catch { setProfileError('No se pudo cambiar la función.'); }
  }

  return (
    <>
      <header className="coke-header">
        <div className="coke-header-brand">
          <NavLink to="/dashboard" className="coke-logo-link" aria-label="Coca-Cola Event Intelligence, inicio" onClick={closeMenu}>
            <img src="/brand/coca-cola-logo-white.png" alt="Coca-Cola" className="coke-header-logo" />
          </NavLink>
          <div className="coke-polar-bear" role="img" aria-label="Oso polar de Coca-Cola saludando">
            <img className="coke-polar-arm" src="/brand/polar-bear-arm.png" alt="" aria-hidden="true" width="1404" height="1120" draggable={false} />
            <img className="coke-polar-body" src="/brand/polar-bear-body.png" alt="" aria-hidden="true" width="1404" height="1120" draggable={false} />
          </div>
        </div>

        <nav aria-label="Navegación principal" className="coke-desktop-nav">
          {navItems.map(({ label, to }) => (
            <NavLink key={to} to={to} end={to === '/dashboard'} className={linkClassName}>
              {label}
            </NavLink>
          ))}
        </nav>

        <button
          type="button"
          aria-label={mobileMenuOpen ? 'Cerrar navegación' : 'Abrir navegación'}
          aria-expanded={mobileMenuOpen}
          aria-controls="mobile-navigation"
          className="coke-menu-toggle"
          onClick={() => {
            setMobileMenuOpen((open) => !open);
          }}
        >
          <span />
          <span />
          <span className="sr-only">{title}</span>
        </button>
        <details className="profile-menu" onKeyDown={event => { if (event.key === 'Escape') event.currentTarget.open = false; }}>
          <summary onClick={closeMenu} aria-label={`Cuenta de ${user?.name}, ${ROLE_LABELS[role]}`}><span className="profile-avatar" aria-hidden="true">{user?.name.slice(0, 1).toUpperCase()}</span><span><strong>{user?.name}</strong><small>{ROLE_LABELS[role]}</small></span><span aria-hidden="true">⌄</span></summary>
          <div className="profile-dropdown">
            <p className="font-bold">Mi cuenta</p><p className="break-all text-sm text-slate-500">{user?.email}</p>
            {user && user.roles.length > 1 && <label className="mt-4 block text-sm font-semibold">Cambiar función
              <select className="coke-input mt-2 w-full" value={role} onChange={event => { if (isUserRole(event.target.value)) void handleRoleChange(event.target.value); }}>
                {user.roles.map(assigned => <option key={assigned} value={assigned}>{ROLE_LABELS[assigned]}</option>)}
              </select>
            </label>}
            {profileError && <p role="alert" className="mt-3 text-sm text-red-700">{profileError}</p>}
            <button className="coke-button mt-4 w-full" onClick={() => { void logout().catch(() => setProfileError('No se pudo cerrar sesión. Intenta nuevamente.')); }}>Cerrar sesión</button>
          </div>
        </details>
      </header>
      {mobileMenuOpen ? (
        <nav id="mobile-navigation" aria-label="Navegación principal" className="coke-mobile-nav">
          {navItems.map(({ label, to }) => (
            <NavLink key={to} to={to} end={to === '/dashboard'} className={linkClassName} onClick={closeMenu}>
              {label}
            </NavLink>
          ))}
        </nav>
      ) : null}
    </>
  );
}
