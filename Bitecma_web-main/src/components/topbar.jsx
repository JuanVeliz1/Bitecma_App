/**
 * Retorna el HTML de la barra superior (breadcrumb, acciones y usuario).
 * Orquesta navegación rápida y acceso a notificaciones/configuración.
 */
import { useState } from 'react'
import logoUrl from '../img/logo.png'
import SvgIcon from './svgIcon.jsx'
import { useApp } from '../context/appContext.jsx'
import { useUi } from '../context/uiContext.jsx'
import { useDb } from '../context/dbContext.jsx'
import { mergeOperacionesById, parseOperacionesPayload, serializeOperaciones } from '../services/operacionesTransferService.js'

function ConfigModalBody() {
  const { navigate, isAdmin } = useApp()
  const { db, setDb } = useDb()
  const { closeModal, toast, theme, setTheme } = useUi()
  const [mode, setMode] = useState('merge')

  const exportOps = () => {
    try {
      const json = serializeOperaciones(db?.operaciones || [])
      const blob = new Blob([json], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const d = new Date()
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const a = document.createElement('a')
      a.href = url
      a.download = `operaciones-${y}${m}${day}.json`
      document.body.appendChild(a)
      a.click()
      a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1200)
      toast('Operaciones exportadas', 'green')
    } catch {
      toast('No se pudo exportar', 'red')
    }
  }

  const importOps = (file) => {
    if (!file) return
    const fr = new FileReader()
    fr.onload = () => {
      try {
        const incoming = parseOperacionesPayload(String(fr.result || ''))
        setDb((prev) => {
          const cur = prev?.operaciones || []
          const nextOps = mode === 'replace' ? incoming : mergeOperacionesById(cur, incoming)
          return { ...prev, operaciones: nextOps }
        })
        toast('Operaciones importadas', 'green')
      } catch {
        toast('Archivo inválido', 'red')
      }
    }
    fr.readAsText(file)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div className="cfg-row">
        <div>
          <div style={{ fontWeight: 800, color: 'var(--navy)' }}>Tema Oscuro</div>
        </div>
        <div className="form-check form-switch" style={{ margin: 0 }}>
          <input
            className="form-check-input"
            type="checkbox"
            role="switch"
            id="cfg-theme"
            checked={theme === 'dark'}
            onChange={(e) => setTheme(e.target.checked ? 'dark' : 'light')}
            style={{ width: '3.2em', height: '1.7em', cursor: 'pointer' }}
          />
        </div>
      </div>

      {isAdmin ? (
        <>
          <div className="cfg-row">
            <div>
              <div style={{ fontWeight: 800, color: 'var(--navy)' }}>Panel Admin</div>
              <div style={{ fontSize: 12, color: 'var(--text3)' }}>Acceso a usuarios/roles y auditoría</div>
            </div>
            <button
              className="btn b-out b-sm"
              onClick={() => {
                closeModal()
                navigate('admin')
              }}
            >
              Abrir
            </button>
          </div>

          <div className="cfg-row">
            <div>
              <div style={{ fontWeight: 800, color: 'var(--navy)' }}>Exportar operaciones</div>
              <div style={{ fontSize: 12, color: 'var(--text3)' }}>Descarga un JSON para migrar a otra PC</div>
            </div>
            <button className="btn b-teal b-sm" onClick={exportOps}>
              Exportar
            </button>
          </div>

          <div className="cfg-row" style={{ alignItems: 'flex-start' }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 800, color: 'var(--navy)' }}>Importar operaciones</div>
              <div style={{ fontSize: 12, color: 'var(--text3)' }}>Carga un JSON exportado previamente</div>
              <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
                <select className="is" style={{ maxWidth: 220 }} value={mode} onChange={(e) => setMode(e.target.value)}>
                  <option value="merge">Combinar por ID</option>
                  <option value="replace">Reemplazar todo</option>
                </select>
                <input className="ii" type="file" accept="application/json,.json" onChange={(e) => importOps(e?.target?.files?.[0])} />
              </div>
            </div>
          </div>
        </>
      ) : (
        <div className="info-box blue">
          <span>i</span>
          <div>Las opciones de migración están disponibles solo para Admin.</div>
        </div>
      )}

      <button className="btn b-teal" onClick={closeModal}>
        Cerrar
      </button>
    </div>
  )
}

export default function Topbar() {
  const { navigate, user, page, role } = useApp()
  const { openModal } = useUi()
  const currentLabel =
    {
      dashboard: 'Dashboard',
      ops: 'Operaciones',
      evadir: 'EVADIR',
      historico: 'Registro Histórico',
      informe: 'Informe',
      especies: 'Especies',
      sectores: 'Sectores',
      orgs: 'Organizaciones',
      botes: 'Botes',
      perfil: 'Perfil',
      admin: 'Admin',
    }[String(page || 'dashboard')] || 'Dashboard'

  return (
    <div className="topbar">
      <div className="tb-logo" onClick={() => navigate('dashboard')}>
        <div className="tb-logo-icon">
          <img src={logoUrl} alt="BITECMA" />
        </div>
        <div className="tb-logo-text">
          BIT<span>ECMA</span>
        </div>
      </div>
      <div className="tb-sep"></div>
      <div className="tb-bc" id="topbc">
        <span>Inicio</span>
        <span>/</span>
        <span className="cur">{currentLabel}</span>
      </div>
      <div className="tb-spacer"></div>
      <div className="tb-actions">
        <button
          className="tb-btn"
          onClick={() =>
            openModal(
              'Notificaciones',
              <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
                <div className="info-box amber">
                  Advertencia
                  <div>
                    <strong>2 outliers en EVADIR HUAPE B</strong>
                    <br />
                    T-4 y T-10 — Revisar densidad
                  </div>
                </div>
                <div className="info-box teal">
                  Operación completada
                  <div>
                    <strong>OP-2026-002 — AMARGOS</strong>
                    <br />
                    5 transectos · 2 botes · muestras L-P ingresadas
                  </div>
                </div>
              </div>,
            )
          }
        >
          <SvgIcon name="bell" aria-hidden="true" />
          <span className="tb-badge">2</span>
        </button>
        <button className="tb-btn" onClick={() => openModal('Configuración', <ConfigModalBody />, 'wide')}>
          <SvgIcon name="gear" aria-hidden="true" />
        </button>
        <div className="user-chip" onClick={() => navigate('perfil')}>
          <div className="user-av" id="tb-user-av">
            {String(user?.nombre || 'US')
              .split(' ')
              .filter(Boolean)
              .slice(0, 2)
              .map((p) => p[0])
              .join('')
              .toUpperCase()}
          </div>
          <div>
            <div className="user-name" id="tb-user-name">
              {user?.nombre || 'Usuario'}
            </div>
            <div className="user-role" id="tb-user-role">
              {role || user?.rol || '—'}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
