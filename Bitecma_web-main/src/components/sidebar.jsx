/**
 * Retorna el HTML del menú lateral (navegación por secciones).
 * Controla el acceso a páginas internas y el cierre de sesión.
 */
import SvgIcon from './svgIcon.jsx'
import { useApp } from '../context/appContext.jsx'

export default function Sidebar() {
  const { page, navigate, logout, isViewer } = useApp()

  return (
    <div className="sidebar">
      <div className="sb-sec">Principal</div>
      <div
        className={`nav ${page === 'dashboard' ? 'on' : ''}`}
        id="nav-dashboard"
        onClick={() => navigate('dashboard')}
      >
        <SvgIcon className="nav-icon" name="grid" aria-hidden="true" />
        Dashboard
      </div>
      <div className="sb-sec">Trabajo de Campo</div>
      {!isViewer ? (
        <div className={`nav ${page === 'ops' ? 'on' : ''}`} id="nav-ops" onClick={() => navigate('ops')}>
          <SvgIcon className="nav-icon" name="folder" aria-hidden="true" />
          Operaciones
        </div>
      ) : null}
      <div className={`nav ${page === 'evadir' ? 'on' : ''}`} id="nav-evadir" onClick={() => navigate('evadir')}>
        <SvgIcon className="nav-icon" name="table" aria-hidden="true" />
        EVADIR
      </div>
      <div className="sb-sec">Maestros</div>
      <div
        className={`nav ${page === 'especies' ? 'on' : ''}`}
        id="nav-especies"
        onClick={() => navigate('especies')}
      >
        <SvgIcon className="nav-icon" name="users" aria-hidden="true" />
        Especies
      </div>
      <div
        className={`nav ${page === 'sectores' ? 'on' : ''}`}
        id="nav-sectores"
        onClick={() => navigate('sectores')}
      >
        <SvgIcon className="nav-icon" name="map" aria-hidden="true" />
        Sectores
      </div>
      <div className={`nav ${page === 'orgs' ? 'on' : ''}`} id="nav-orgs" onClick={() => navigate('orgs')}>
        <SvgIcon className="nav-icon" name="users" aria-hidden="true" />
        Organizaciones
      </div>
      <div
        className={`nav ${page === 'botes' ? 'on' : ''}`}
        id="nav-botes"
        onClick={() => navigate('botes')}
      >
        <SvgIcon className="nav-icon" name="anchor" aria-hidden="true" />
        Botes
      </div>
      <div className="sb-foot">
        <div
          className="nav"
          style={{ color: 'var(--red)' }}
          onClick={logout}
        >
          <SvgIcon className="nav-icon" name="logout" aria-hidden="true" />
          Cerrar sesión
        </div>
      </div>
    </div>
  )
}
