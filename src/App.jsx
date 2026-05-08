import AdminPage from './pages/admin.jsx'
import BotesPage from './pages/botes.jsx'
import DashboardPage from './pages/dashboard.jsx'
import EspeciesPage from './pages/especies.jsx'
import EvadirPage from './pages/evadir.jsx'
import HistoricoPage from './pages/historico.jsx'
import InformePage from './pages/informe.jsx'
import LoginScreen from './pages/login.jsx'
import OpsPage from './pages/ops.jsx'
import OrgsPage from './pages/orgs.jsx'
import PerfilPage from './pages/perfil.jsx'
import SectoresPage from './pages/sectores.jsx'

import Topbar from './components/topbar.jsx'
import Sidebar from './components/sidebar.jsx'
import { AppProvider, useApp } from './context/appContext.jsx'
import { DbProvider } from './context/dbContext.jsx'
import { UiProvider, useUi } from './context/uiContext.jsx'

function ToastHost() {
  const { toastState } = useUi()
  const bg =
    toastState.type === 'green'
      ? '#065f46'
      : toastState.type === 'red'
        ? '#7f1d1d'
        : 'var(--navy)'
  return (
    <div className={`toast${toastState.show ? ' show' : ''}`} style={{ background: bg }}>
      <span>{toastState.msg}</span>
    </div>
  )
}

function ModalHost() {
  const { modalState, closeModal } = useUi()
  return (
    <div
      className={`mo${modalState.open ? ' open' : ''}`}
      onClick={(e) => {
        if (e.target !== e.currentTarget) return
        closeModal()
      }}
    >
      <div className={`mb-box${modalState.size ? ' ' + modalState.size : ''}`}>
        <div className="mh">
          <h3>{modalState.title}</h3>
          <button className="mc" onClick={closeModal}>
            ×
          </button>
        </div>
        <div>{modalState.body}</div>
      </div>
    </div>
  )
}

function AppShell() {
  const { page, isAuthed } = useApp()
  return (
    <>
      <ToastHost />
      <ModalHost />

      <LoginScreen active={!isAuthed} />

      <div id="scr-app" className={`screen${isAuthed ? ' active' : ''}`}>
        <Topbar />
        <div className="app-body">
          <Sidebar />
          <div className="main">
            <DashboardPage active={page === 'dashboard'} />
            <OpsPage active={page === 'ops'} />
            <EvadirPage active={page === 'evadir'} />
            <HistoricoPage active={page === 'historico'} />
            <InformePage active={page === 'informe'} />
            <EspeciesPage active={page === 'especies'} />
            <SectoresPage active={page === 'sectores'} />
            <OrgsPage active={page === 'orgs'} />
            <BotesPage active={page === 'botes'} />
            <PerfilPage active={page === 'perfil'} />
            <AdminPage active={page === 'admin'} />
          </div>
        </div>
      </div>
    </>
  )
}

export default function App() {
  return (
    <DbProvider>
      <UiProvider>
        <AppProvider>
          <AppShell />
        </AppProvider>
      </UiProvider>
    </DbProvider>
  )
}
