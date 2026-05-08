import { useMemo, useState } from 'react'
import { useDb } from '../context/dbContext.jsx'

export default function BotesPage({ active }) {
  const { db } = useDb()
  const regiones = useMemo(() => {
    const arr = db?.regionesChile
    return Array.isArray(arr) ? arr : []
  }, [db?.regionesChile])
  const botes = useMemo(() => {
    const arr = db?.botesMaestro
    return Array.isArray(arr) ? arr : []
  }, [db?.botesMaestro])

  const [regionRom, setRegionRom] = useState(regiones[0]?.rom || 'I')
  const [q, setQ] = useState('')
  const botesFiltrados = useMemo(() => {
    const query = String(q || '').toLowerCase().trim()
    return botes
      .filter((b) => String(b.region || '') === String(regionRom || ''))
      .filter((b) =>
        !query
          ? true
          : String(b.nombre || '').toLowerCase().includes(query) ||
            String(b.caleta || '').toLowerCase().includes(query) ||
            String(b.nrpa || '').toLowerCase().includes(query) ||
            String(b.nmatricula || '').toLowerCase().includes(query),
      )
      .sort((a, b) => (Number(a.id) || 0) - (Number(b.id) || 0))
      .slice(0, 2000)
  }, [botes, regionRom, q])

  return (
    <div className={`page${active ? ' active' : ''}`} id="pg-botes">
      <div className="ph">
        <div>
          <h2>Botes</h2>
          <p>Listado de botes y embarcaciones por región</p>
        </div>
      </div>
      <div className="admin-layout" id="mb-layout" style={{ height: 'calc(100vh - 190px)', alignItems: 'stretch' }}>
        <div className="card admin-menu" style={{ minHeight: 0, overflowY: 'auto' }}>
          {regiones.map((r) => (
            <div
              key={r.id}
              className={`admin-item ${regionRom === r.rom ? 'on' : ''}`}
              onClick={() => setRegionRom(r.rom)}
            >
              {r.rom} — {r.nom}
            </div>
          ))}
        </div>
        <div id="mb-right" style={{ minHeight: 0 }}>
          <div className="card admin-content" style={{ height: '100%', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
            <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
              <input className="flt" placeholder="Buscar bote..." value={q} onChange={(e) => setQ(e.target.value)} />
            </div>
            <div style={{ overflow: 'auto', minHeight: 0 }}>
              <table className="tbl">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Nombre</th>
                    <th>Caleta</th>
                    <th>NRPA</th>
                    <th>Matrícula</th>
                  </tr>
                </thead>
                <tbody>
                  {botesFiltrados.length ? (
                    botesFiltrados.map((b) => (
                      <tr key={b.id}>
                        <td>{b.id}</td>
                        <td>
                          <strong>{b.nombre}</strong>
                        </td>
                        <td>{b.caleta || '—'}</td>
                        <td>{b.nrpa || '—'}</td>
                        <td>{b.nmatricula || '—'}</td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text3)', padding: 14 }}>Sin resultados</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
