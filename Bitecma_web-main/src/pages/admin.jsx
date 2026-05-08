import { useEffect, useMemo, useState } from 'react'
import { getAuditEntries } from '../services/auditService.js'
import { useDb } from '../context/dbContext.jsx'
import { useUi } from '../context/uiContext.jsx'
import { useApp } from '../context/appContext.jsx'

export default function AdminPage({ active }) {
  const { db } = useDb()
  const { toast } = useUi()
  const { navigate, isAdmin } = useApp()
  const [tab, setTab] = useState('usuarios')

  const perfiles = useMemo(() => {
    const arr = db?.perfiles
    return Array.isArray(arr) ? arr : []
  }, [db?.perfiles])

  const auditEntries = useMemo(() => getAuditEntries(), [])

  useEffect(() => {
    if (!active) return
    if (isAdmin) return
    toast('Acceso restringido: solo Admin', 'red')
    navigate('dashboard')
  }, [active, isAdmin, navigate, toast])

  const rolePillClass = (rol) => {
    const r = String(rol || '').toLowerCase()
    if (r === 'admin') return 'p-red'
    if (r === 'usuario') return 'p-grn'
    if (r === 'visualizador') return 'p-amb'
    if (r === 'biólogo' || r === 'biologo') return 'p-grn'
    return 'p-slt'
  }

  const usuariosRows = perfiles
    .map((u) => {
      const rol = u?.rol || '—'
      return (
        <tr key={u?.id}>
          <td>{u?.id}</td>
          <td>
            <strong>{u?.nombre || '—'}</strong>
          </td>
          <td>{u?.correo || '—'}</td>
          <td>
            <span className={`pill ${rolePillClass(rol)}`}>{rol}</span>
          </td>
          <td>
            <span className={`pill ${u?.activo === false ? 'p-amb' : 'p-grn'}`}>
              {u?.activo === false ? 'Inactivo' : 'Activo'}
            </span>
          </td>
          <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
            <button className="btn b-out b-xs" onClick={() => toast('Editar usuario (backend)')}>
              Editar
            </button>{' '}
            <button className="btn b-out b-xs" onClick={() => toast('Reset contraseña (backend)')}>
              Reset
            </button>
          </td>
        </tr>
      )
    })
    .filter(Boolean)

  const auditoriaRows = auditEntries.map((r, idx) => {
    const p = perfiles.find((x) => String(x.id) === String(r.userId))
    const rol = p?.rol || r.rol || '—'
    const user = p?.nombre || r.userName || `U-${String(r.userId || '')}`
    return (
      <tr key={`${r.ts || ''}-${idx}`}>
        <td style={{ whiteSpace: 'nowrap' }}>{r.ts || '—'}</td>
        <td>{user}</td>
        <td>
          <span className={`pill ${rolePillClass(rol)}`}>{rol}</span>
        </td>
        <td>
          <strong>{r.action || '—'}</strong>
        </td>
        <td style={{ color: 'var(--text2)' }}>{r.detail || '—'}</td>
      </tr>
    )
  })

  return (
    <div className={`page${active ? ' active' : ''}`} id="pg-admin">
      <div className="ph">
        <div>
          <h2>Panel Admin</h2>
          <p>Gestión de usuarios, roles y auditoría</p>
        </div>
        <div className="ph-a">
          <button className="btn b-out" onClick={() => navigate('dashboard')}>
            Volver
          </button>
        </div>
      </div>
      <div className="admin-layout">
        <div className="admin-menu card">
          <div className={`admin-item ${tab === 'usuarios' ? 'on' : ''}`} onClick={() => setTab('usuarios')}>
            Usuarios
          </div>
          <div className={`admin-item ${tab === 'roles' ? 'on' : ''}`} onClick={() => setTab('roles')}>
            Roles y Accesos
          </div>
          <div className={`admin-item ${tab === 'auditoria' ? 'on' : ''}`} onClick={() => setTab('auditoria')}>
            Auditoría
          </div>
        </div>
        <div className="admin-content card">
          {tab === 'usuarios' ? (
            <>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <div>
                  <div style={{ fontFamily: 'var(--ff-d)', fontSize: 16, fontWeight: 800, color: 'var(--navy)' }}>
                    Usuarios
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text3)', marginTop: 2 }}>
                    Crear, editar, desactivar y asignar roles
                  </div>
                </div>
                <button className="btn b-teal" onClick={() => toast('Crear usuario (backend)')}>
                  + Nuevo usuario
                </button>
              </div>
              <div style={{ overflowX: 'auto' }}>
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Nombre</th>
                      <th>Correo</th>
                      <th>Rol</th>
                      <th>Estado</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {usuariosRows.length ? (
                      usuariosRows
                    ) : (
                      <tr>
                        <td colSpan={6} style={{ textAlign: 'center', color: 'var(--text3)', padding: 14 }}>
                          Sin usuarios
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </>
          ) : null}

          {tab === 'roles' ? (
            <>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <div>
                  <div style={{ fontFamily: 'var(--ff-d)', fontSize: 16, fontWeight: 800, color: 'var(--navy)' }}>
                    Roles y Accesos
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text3)', marginTop: 2 }}>Matriz de permisos por rol</div>
                </div>
                <button className="btn b-out" onClick={() => toast('Editar matriz (backend)')}>
                  Editar
                </button>
              </div>
              <div style={{ overflowX: 'auto' }}>
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>Acción / Módulo</th>
                      <th>Admin</th>
                      <th>Usuario</th>
                      <th>Visualizador</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>
                        <strong>Operaciones</strong> (crear/editar)
                      </td>
                      <td>✔</td>
                      <td>✔</td>
                      <td>Ver</td>
                    </tr>
                    <tr>
                      <td>
                        <strong>EVADIR</strong> (generar/exportar)
                      </td>
                      <td>✔</td>
                      <td>✔</td>
                      <td>Ver</td>
                    </tr>
                    <tr>
                      <td>
                        <strong>Maestros</strong> (especies/sectores)
                      </td>
                      <td>✔</td>
                      <td>✔</td>
                      <td>Ver</td>
                    </tr>
                    <tr>
                      <td>
                        <strong>Usuarios</strong> (gestión)
                      </td>
                      <td>✔</td>
                      <td>—</td>
                      <td>—</td>
                    </tr>
                    <tr>
                      <td>
                        <strong>Auditoría</strong> (visualización)
                      </td>
                      <td>✔</td>
                      <td>—</td>
                      <td>—</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </>
          ) : null}

          {tab === 'auditoria' ? (
            <>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <div>
                  <div style={{ fontFamily: 'var(--ff-d)', fontSize: 16, fontWeight: 800, color: 'var(--navy)' }}>
                    Auditoría
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text3)', marginTop: 2 }}>Historial de acciones de usuarios</div>
                </div>
                <button className="btn b-out" onClick={() => toast('Exportar auditoría (backend)')}>
                  Exportar
                </button>
              </div>
              <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
                <select className="flt" onChange={() => toast('Filtrar por usuario (backend)')}>
                  <option>Todos los usuarios</option>
                  <option>U-001</option>
                  <option>U-002</option>
                  <option>U-003</option>
                </select>
                <select className="flt" onChange={() => toast('Filtrar por acción (backend)')}>
                  <option>Todas las acciones</option>
                  <option>Editó operación</option>
                  <option>Generó EVADIR</option>
                  <option>Cambió rol</option>
                </select>
                <input className="flt" placeholder="Buscar..." onInput={() => toast('Buscar (backend)')} />
              </div>
              <div style={{ overflowX: 'auto' }}>
                <table className="tbl">
                  <thead>
                    <tr>
                      <th>Fecha</th>
                      <th>Usuario</th>
                      <th>Rol</th>
                      <th>Acción</th>
                      <th>Detalle</th>
                    </tr>
                  </thead>
                  <tbody>
                    {auditoriaRows.length ? (
                      auditoriaRows
                    ) : (
                      <tr>
                        <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text3)', padding: 14 }}>
                          Sin auditoría registrada
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </>
          ) : null}
        </div>
      </div>
    </div>
  )
}
